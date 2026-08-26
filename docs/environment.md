# Environment notes

Published results use GitHub-hosted runners, one voter per scenario, and durable storage. They describe the measured implementation paths, not mainnet capacity.

## Pinned toolchain

| Component | Version |
| --- | --- |
| Gradle wrapper | 9.2.1 |
| Kotlin/JVM | 2.4.10 |
| Java toolchain | 21 |
| Ktor | 3.5.2 |
| Kotlin coroutines / serialization | 1.11.0 |
| Testcontainers | 2.0.5 |
| `cash.atto:commons-core-jvm` / `commons-worker-jvm` | 7.0.2 |
| Bouncy Castle `bcprov-jdk18on` | 1.84 |
| `net.i2p.crypto:eddsa` | 0.3.0 |

## Pinned implementations

| Implementation | Official image | Network and storage |
| --- | --- | --- |
| Atto | `ghcr.io/attocash/node:1.34-live` | One `local` voter backed by `mysql:8.4` on a fresh disk-backed bind mount with MySQL durability defaults. |
| Nano | `nanocurrency/nano:V28.2` | `dev` network with a fresh on-disk LMDB data directory and default durable synchronization. |
| RSNano | `rsnano/rsnano:V3.1` | `dev` network with a fresh on-disk LMDB data directory and `sync = "always"`. |

No published profile uses tmpfs or durability-reducing database flags. Nano's `dev` network is distinct from its `test` network; `test` defaults to live-level work and is not used.

Each scenario starts a fresh Testcontainers environment and closes it afterward. Startup checks verify the expected Nano/RSNano vendor, build, `dev` network identifier, and canonical genesis frontier before installing the public dev voting key. Atto startup verifies the canonical `LOCAL` genesis before starting one official voter and its MySQL database.

Useful source commits and image references are retained in [`provenance/source-revisions.json`](../provenance/source-revisions.json). Each run manifest resolves the actual image digests and records Java, OS, CPU, storage, and sanitized runtime configuration beside the samples.

## Published result environment

The published results come from [GitHub Actions run 33016888792](https://github.com/rotilho/feeless-benchmarks/actions/runs/33016888792). Ten paired jobs ran on ten GitHub-hosted VMs. Within each job, Atto, Nano, and RSNano ran sequentially on the same VM, with a fresh Testcontainers environment for every scenario. The three serial scenarios ran as independent jobs.

Every runner reported Linux kernel `6.17.0-1022-azure`, four logical processors, Eclipse Adoptium OpenJDK 21.0.12 or 21.0.12.1, and one of four CPU models: AMD EPYC 7763, AMD EPYC 9V74, Intel Xeon Platinum 8573C, or Intel Xeon 6973P-C. Every manifest records `storage_profile = durable` and the resolved node image digest; the Atto manifests also record the MySQL 8.4 digest and default database durability.

Published manifests: [`nano-serial`](../results/common-runner/nano-serial/nano-serial-manifest.json), [`atto-serial`](../results/common-runner/atto-serial/atto-serial-manifest.json), [`rsnano-serial`](../results/common-runner/rsnano-serial/rsnano-serial-manifest.json), [`nano-500`](../results/common-runner/nano-500/nano-500-manifest.json), [`atto-500`](../results/common-runner/atto-500/atto-500-manifest.json), and [`rsnano-500`](../results/common-runner/rsnano-500/rsnano-500-manifest.json).

Hardware, container runtime, host scheduling, storage implementations, and notification overhead affect measured values. GitHub-hosted runner variation remains visible between paired jobs, while running all three implementations on the same VM within each job reduces cross-implementation runner bias. A rerun is a distinct result set even with identical tags and fixtures.
