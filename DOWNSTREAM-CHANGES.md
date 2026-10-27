# Downstream source changes

Upstream: https://github.com/CloudburstMC/Network

Base commit: `6cf609e05cdcbc330e2c0d4cbea4c00fef006669`.

This repository preserves upstream history and is an independent source copy, not an official upstream release. GitHub public forks cannot be made private, so it was first created as a private standalone repository.

## Changes (2026-10-27)

Adds a signaling-address inference attempt before trickled remote candidates are delivered. The switch is `nethernet.predictSignalingCandidate` (default true). This is experimental: upstream already has `SdpUtil.inferredPeerCandidates`, so the additional inference overlaps existing behavior. Its independent benefit has not been established, and it is not presented as a required connectivity fix.

The changes preserve the previous tested source implementation. Publication coordinates are deliberately separate: `2.0.0.CR6-nekoy3-turn-SNAPSHOT`.

## Build

Use JDK 17 for the build/NetherNet modules and a JDK 8 toolchain for the other modules, or permit the configured Gradle resolver to provision toolchains.

```sh
./gradlew :transport-nethernet:test --tests '*SdpUtilTest' --tests '*ForwardedForTest' publishToMavenLocal -x javadoc
```

The selected existing tests check SDP handling and trusted-forwarded address behavior. They do not establish an independent connectivity benefit for the experimental inference. No prebuilt runtime artifact is published.

## License and attribution

The original Apache License 2.0 `LICENSE` and source notices are preserved unchanged. Downstream additions use Apache-2.0. Modified files prominently identify the change and date. The upstream tracked tree contains no separate NOTICE file at the pinned base; existing notices remain in the source and history. This is an unofficial downstream source copy and does not imply upstream endorsement or transfer of its trademarks.

See [Apache License 2.0, section 4](https://www.apache.org/licenses/LICENSE-2.0#redistribution). When distributing a combined binary with other projects, check those licenses separately; keeping this source under Apache-2.0 is not a blanket permission for every combination.

## Review

Compare `upstream-base` with `codex/signaling-candidate` or `main`.
