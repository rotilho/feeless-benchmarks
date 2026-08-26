# feeless-benchmarks

A benchmark suite for controlled, single-host measurements of pinned feeless account-chain implementations.

> These are one-voter implementation measurements. They are not protocol limits, production-network tests, or mainnet-capacity claims.

## Project layout

The root Gradle build is the only build, and `app` is the only executable.

| Module | Responsibility |
| --- | --- |
| [`benchmark-core`](benchmark-core/) | Typed benchmark contracts, lane execution, raw samples, statistics, CSV, summary JSON, and run manifests. It has no Ktor, Testcontainers, or cryptocurrency dependency. |
| [`benchmark-nano`](benchmark-nano/) | Offline Nano fixture/crypto support, the shared Nano/RSNano RPC and post-cement adapter, and `NanoNodeSpec`. RSNano remains a `NanoNodeSpec` because it shares the Nano protocol and fixtures. |
| [`benchmark-atto`](benchmark-atto/) | Deterministic Atto fixtures, the strict final-stream adapter, and the Atto node plus MySQL Testcontainers environment. |
| [`benchmark-app`](benchmark-app/) | CLI orchestration, fixture promotion, serial suite execution, fresh-environment lifecycle, result manifests, and cross-run ranges. |

## Commands

Run all commands from the repository root:

```bash
./gradlew :app:run --args='generate-fixtures --implementation=all'
./gradlew :app:run --args='validate-fixtures'
./gradlew :app:run --args='run --implementation=nano --fixture=nano-serial --output-dir=results/reproduction/nano-serial'
./gradlew :app:run --args='run-suite --implementations=nano,atto,rsnano --output-root=results/reproduction/full-suite'
./gradlew :app:run --args='aggregate-results --input-root=results/reproduction/full-benchmark --output-dir=results/reproduction/full-benchmark-ranges'
```

Benchmark output paths supplied to `run` and `run-suite` must not already exist. All coroutine work uses one process-wide Java 21 virtual-thread dispatcher, recorded as `coroutine.dispatcher=virtual` in each new manifest. `run-suite` executes every selected scenario serially, with a fresh Testcontainers environment for each scenario. `aggregate-results` reads completed 500 × 100 shards; `--implementations` defaults to `nano,atto,rsnano`, `--expected-runs` to `10`, and `--account-count` to `500`. It writes exact ranges to `500-account-ranges.json` and a whole-millisecond presentation to `500-account-ranges.md`. See the [reproduction guide](docs/reproduction.md) for fixture names, optional arguments, and prerequisites.

Verification is split between fast checks and container smoke tests:

```bash
./gradlew check
./gradlew containerIntegrationTest
```

## Full benchmark workflow

> [!WARNING]
> The **Full benchmark** workflow is useful for reproducibility checks and detecting large regressions, but its timings are not reliable enough for the published cross-implementation comparison. Each repeated job runs Atto, Nano, and RSNano on the same [GitHub-hosted VM](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), which reduces runner differences within that comparison. The ten jobs still use ten fresh VMs whose CPU, ephemeral storage, network conditions, and host contention may differ. The ranges below were therefore collected serially on one dedicated physical computer.

The manual **Full benchmark** workflow starts thirteen benchmark jobs: one 1 × 1,000 serial job for each implementation and ten paired 500 × 100 jobs. Each paired job gets one runner and executes Atto, Nano, and RSNano sequentially, creating a fresh node/database environment for every scenario. Their order rotates across the ten jobs so each implementation appears first, second, and third either three or four times. GitHub account concurrency limits may leave jobs queued.

Both matrices use `fail-fast: false`. Within a paired job, a failed scenario does not prevent the other implementations from running. The range job waits for all ten paired jobs and accepts the thirty 500 × 100 summaries only when every shard is valid. A missing or invalid shard leaves the workflow red and incomplete; it does not publish partial ranges. The aggregate artifact contains exact JSON values and a Markdown view with latency rounded to whole milliseconds.

## Measurement contract

`BenchmarkEngine` is the only owner of `System.nanoTime()`. Setup publication is unmeasured. Lanes run concurrently, while items in a lane remain serial. A successful sample reads the clock exactly before publication and after the adapter observes the exact external completion signal. A sample failure stops only its lane and leaves completion and latency null; coroutine cancellation propagates.

The suite retains one authoritative raw row per attempted measured item. Summary JSON is calculated from those rows with arithmetic mean, nearest-rank percentiles, and a half-open one-second peak-completion window. Micrometer and node-provided timing metrics are not part of the result path. Each run adds a separate manifest with the runner revision, fixture hashes, resolved image digests, Java/OS/CPU details, the durable storage profile, and sanitized runtime configuration.

## Published results

The serial rows are one accepted run each. The 500 × 100 rows show the minimum and maximum from ten complete run summaries per implementation, collected serially on the same dedicated computer. The ranges are not calculated by pooling samples. All thirty repeated runs used fresh node/database environments, completed 50,000 samples, and reported zero errors.

| Workload | Scenario | Runs | Average (ms) ↓ | p50 (ms) ↓ | p90 (ms) ↓ | p95 (ms) ↓ | p99 (ms) ↓ | Average TPS ↑ | Peak TPS ↑ | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 × 1,000 serial | Nano V28.2 (`nano-serial`) | 1 | 200 | 200 | 205 | 205 | 209 | 4.99 | 6 | [CSV](results/common-runner/nano-serial/nano-serial-samples.csv) · [summary](results/common-runner/nano-serial/nano-serial-summary.json) · [manifest](results/common-runner/nano-serial/nano-serial-manifest.json) |
| 1 × 1,000 serial | Atto 1.34 (`atto-serial`) | 1 | 16 | 16 | 21 | 23 | 28 | 60.67 | 71 | [CSV](results/common-runner/atto-serial/atto-serial-samples.csv) · [summary](results/common-runner/atto-serial/atto-serial-summary.json) · [manifest](results/common-runner/atto-serial/atto-serial-manifest.json) |
| 1 × 1,000 serial | RSNano V3.1 (`rsnano-serial`) | 1 | 200 | 200 | 202 | 204 | 207 | 4.99 | 6 | [CSV](results/common-runner/rsnano-serial/rsnano-serial-samples.csv) · [summary](results/common-runner/rsnano-serial/rsnano-serial-summary.json) · [manifest](results/common-runner/rsnano-serial/rsnano-serial-manifest.json) |
| 500 × 100 | Nano V28.2 (`nano-500`) | 10 | 871–1,504 | 872–941 | 996–1,113 | 1,010–7,794 | 1,051–8,011 | 331.03–569.99 | 690–784 | [range JSON](results/common-runner/500-account-ranges.json) |
| 500 × 100 | Atto 1.34 (`atto-500`) | 10 | 157–211 | 131–191 | 243–306 | 271–333 | 301–477 | 2,348.74–3,172.70 | 3,026–4,052 | [range JSON](results/common-runner/500-account-ranges.json) |
| 500 × 100 | RSNano V3.1 (`rsnano-500`) | 10 | 916–1,316 | 875–1,304 | 1,403–2,094 | 1,530–2,317 | 1,798–2,798 | 374.80–543.05 | 664–984 | [range JSON](results/common-runner/500-account-ranges.json) |

Each range is the lowest-to-highest run-level value, not a confidence interval. Latency is rounded to the nearest whole millisecond for display; the [range report](results/common-runner/500-account-ranges.md), JSON, summaries, and CSVs retain the authoritative precision available at their respective level. Nano and RSNano complete on the exact block's post-cement event. Atto completes when the stream response returns the matching transaction. RSNano shares Nano's fixtures.

## Pinned toolchain and implementations

| Component | Pin |
| --- | --- |
| Gradle | 9.2.1 |
| Kotlin | 2.4.10 |
| Java | 21 |
| Ktor | 3.5.2 |
| Coroutines / serialization | 1.11.0 |
| Testcontainers | 2.0.5 |
| Atto Commons | 7.0.2 JVM variants |
| Atto node / database | `ghcr.io/attocash/node:1.34-live` / `mysql:8.4` |
| Nano | `nanocurrency/nano:V28.2` on the `dev` network |
| RSNano | `rsnano/rsnano:V3.1` on the `dev` network, LMDB `sync = "always"` |

Published runs use durable storage only: no tmpfs and no durability-reducing database flags.

Further documentation:

- [Methodology and timing](docs/methodology.md)
- [Benchmark specification](docs/benchmark-spec.md)
- [Raw, summary, and manifest fields](docs/data-dictionary.md)
- [Fixture generation and validation](fixtures/README.md)
- [Results index and replacement policy](results/README.md)
- [Reproduction](docs/reproduction.md)
- [Pinned source revisions](provenance/source-revisions.json)
