/*
 * Modified by nekoy3 on 2026-10-27: experimental signaling-address candidate inference.
 *
 * Copyright 2026 CloudburstMC
 *
 * CloudburstMC licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.cloudburstmc.netty.channel.nethernet;

import io.netty.util.NetUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.cloudburstmc.netty.util.nethernet.EndpointAddress;
import org.cloudburstmc.netty.util.nethernet.SdpUtil;
import tel.schich.libdatachannel.PeerConnection;
import tel.schich.libdatachannel.SessionDescriptionType;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies the remote side's candidates to one peer connection.
 */
final class RemoteCandidates {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(RemoteCandidates.class);
    /** Taken from the description, and again from trickling, far more than a peer gathers. */
    static final int MAX_CANDIDATES = 32;
    private static final boolean PREDICT_SIGNALING_CANDIDATE =
            SystemPropertyUtil.getBoolean("nethernet.predictSignalingCandidate", true);

    private final PeerConnection peer;
    private final String connectionId;
    private final boolean ipOnly;
    private final AtomicInteger trickled = new AtomicInteger();

    private RemoteCandidates(PeerConnection peer, String connectionId, boolean ipOnly) {
        this.peer = peer;
        this.connectionId = connectionId;
        this.ipOnly = ipOnly;
    }

    /**
     * For a server, which takes only IP literals, at most {@link #MAX_CANDIDATES} of them from the offer and again
     * from trickling.
     */
    static RemoteCandidates server(PeerConnection peer, String connectionId) {
        return new RemoteCandidates(peer, connectionId, true);
    }

    /**
     * For a client, which takes what the server sends as it is. A server may name a host inside its own network, such
     * as a cluster DNS name, for libdatachannel to resolve.
     */
    static RemoteCandidates client(PeerConnection peer, String connectionId) {
        return new RemoteCandidates(peer, connectionId, false);
    }

    /**
     * Applies the remote description, keeping the candidates {@link #trickle} would take.
     */
    void setDescription(String sdp, SessionDescriptionType type) {
        this.peer.setRemoteDescription(this.ipOnly ? SdpUtil.withIpCandidates(sdp, MAX_CANDIDATES) : sdp, type);
    }

    /** Adds the signaling IP early enough for TURN to grant permission before the peer's checks arrive. */
    void predictSignalingCandidate(String sdp, InetSocketAddress clientAddress) {
        if (!PREDICT_SIGNALING_CANDIDATE || clientAddress == null) {
            return;
        }
        InetAddress address = clientAddress.getAddress();
        if (address == null || EndpointAddress.scope(address) != EndpointAddress.Scope.PUBLIC) {
            return;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4 && (raw[0] & 255) == 192 && raw[1] == 0 && raw[2] == 0) {
            return;
        }

        int port = clientAddress.getPort();
        boolean foundPort = false;
        for (String line : sdp.split("\r\n|\n")) {
            if (!line.startsWith("a=candidate:")) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                continue;
            }
            InetAddress candidateAddress = NetUtil.createInetAddressFromIpAddressString(parts[4]);
            if (address.equals(candidateAddress)) {
                return;
            }
            if (!foundPort && candidateAddress != null
                    && candidateAddress.getAddress().length == raw.length
                    && "udp".equalsIgnoreCase(parts[2]) && "typ".equals(parts[6]) && "host".equals(parts[7])) {
                try {
                    int candidatePort = Integer.parseInt(parts[5]);
                    if (candidatePort > 0 && candidatePort <= 65535) {
                        port = candidatePort;
                        foundPort = true;
                    }
                } catch (NumberFormatException e) {
                    // A malformed port cannot supply the predicted endpoint
                }
            }
        }
        if (port == 0) {
            return;
        }
        // RFC 8445: srflx type preference 100, local preference 65535, component 1.
        String candidate = "candidate:90000008 1 udp 1694498815 " + address.getHostAddress() + " " + port
                + " typ srflx raddr " + (raw.length == 4 ? "0.0.0.0" : "::") + " rport 0";
        log.debug("Adding predicted signaling candidate for {}: {}", this.connectionId, candidate);
        this.add(candidate);
    }

    /**
     * Applies a candidate the remote side trickled.
     */
    void trickle(String candidate) {
        if (this.ipOnly && (!SdpUtil.hasIpAddress(candidate) || this.trickled.incrementAndGet() > MAX_CANDIDATES)) {
            log.debug("Ignoring remote candidate for {}: {}", this.connectionId, candidate);
            return;
        }
        this.add(candidate);
    }

    /**
     * Applies a candidate, logging rather than throwing once the peer is gone.
     */
    void add(String candidate) {
        log.trace("Applying remote candidate for {}: {}", this.connectionId, candidate);
        try {
            this.peer.addRemoteCandidate(candidate);
        } catch (Exception e) {
            log.debug("Failed to apply remote candidate for {} (connection likely closed): {}",
                    this.connectionId, e.toString());
        }
    }
}
