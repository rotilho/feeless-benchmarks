# Reproduction

## Prerequisites

- Linux AMD64 host
- Java 21 (the Gradle toolchain target)
- Docker Engine, or a Docker-compatible Podman API reachable by Testcontainers
- Network access to Maven Central and the pinned public container images
- Enough durable local storage for fresh node/database data and output artifacts

The Gradle 9.2.1 wrapper resolves all dependencies.

## Verify the runner

Fast checks exclude container lifecycle tests:

```bash
./gradlew check
```

Run the three implementation smoke paths separately:

```bash
./gradlew containerIntegrationTest
```

The second task starts pinned Testcontainers environments for Atto, Nano, and RSNano and requires small fixture publication to reach exact-hash completion.

## Generate and validate fixtures

Generate both protocol families:

```bash
./gradlew :app:run --args='generate-fixtures --implementation=all'
```

Or select `atto` or `nano`:

```bash
./gradlew :app:run --args='generate-fixtures --implementation=nano --fixtures-dir=fixtures'
```

Generation runs twice in temporary directories, requires byte-identical outputs, validates them, and only then atomically replaces each canonical file. Validate the canonical directory independently with:

```bash
./gradlew :app:run --args='validate-fixtures'
```

Use `--fixtures-dir=PATH` to validate another directory. The Nano fixtures declare schema `nano-v28.2-benchmark-fixture/v2` and include source provenance, V28.2 epoch/subtype thresholds, and section SHA-256 checksums.

## Run one scenario

Fixture names are `nano-serial`, `nano-500`, `atto-serial`, and `atto-500`. RSNano consumes either Nano fixture.

```bash
./gradlew :app:run --args='run --implementation=nano --fixture=nano-serial --output-dir=results/reproduction/nano-serial'
./gradlew :app:run --args='run --implementation=atto --fixture=atto-serial --output-dir=results/reproduction/atto-serial'
./gradlew :app:run --args='run --implementation=rsnano --fixture=nano-serial --output-dir=results/reproduction/rsnano-serial'
./gradlew :app:run --args='run --implementation=nano --fixture=nano-500 --output-dir=results/reproduction/nano-500'
./gradlew :app:run --args='run --implementation=atto --fixture=atto-500 --output-dir=results/reproduction/atto-500'
./gradlew :app:run --args='run --implementation=rsnano --fixture=nano-500 --output-dir=results/reproduction/rsnano-500'
```

The default per-item timeout is 60 seconds. Override it with `--timeout-seconds=SECONDS`. All coroutine work uses the runner's process-wide Java 21 virtual-thread dispatcher, recorded as `coroutine.dispatcher=virtual` in the manifest. Each output directory must not already exist.

Every run creates a fresh environment and writes:

```text
<scenario>-samples.csv
<scenario>-summary.json
<scenario>-manifest.json
```

For current runs, `<scenario>` is the output name shown above. The manifest records the fixture identifier and hashes, runner revision, resolved image digests, Java/OS/CPU details, durable storage profile, and sanitized runtime settings. The raw CSV remains the authority for every aggregate.

## Run the serial suite

```bash
./gradlew :app:run --args='run-suite --implementations=nano,atto,rsnano --output-root=results/reproduction/full-suite'
```

`--implementations` defaults to `nano,rsnano,atto`. Use `--fixtures-dir=PATH` or `--timeout-seconds=SECONDS` when needed. The output root must not exist. The suite runs each selected implementation's 1 × 1,000 and 500 × 100 scenarios serially, creating a fresh node/database environment for every scenario.

## Aggregate parallel 500-account runs

Aggregate a complete set of independent 500 × 100 runs with:

```bash
./gradlew :app:run --args='aggregate-results --input-root=results/reproduction/full-benchmark --output-dir=results/reproduction/full-benchmark-ranges'
```

The full form is `aggregate-results --input-root=PATH --output-dir=PATH [--implementations=nano,atto,rsnano] [--expected-runs=10] [--account-count=500]`. The command requires the expected clean shard set and writes:

```text
500-account-ranges.json
500-account-ranges.md
```

The JSON retains exact values. The Markdown rounds latency to the nearest whole millisecond for presentation. Run aggregation only after every input shard has passed acceptance; the range files do not replace the underlying CSV, summary, or manifest.

## Run the Full benchmark workflow

The **Full benchmark** workflow runs daily at 05:17 UTC and can also be dispatched manually. It starts three independent 1 × 1,000 jobs and ten paired 500 × 100 jobs. Each paired job receives one GitHub-hosted runner and executes Atto, Nano, and RSNano sequentially, with a fresh node/database environment for every scenario. Their order rotates across the ten jobs so each implementation appears first, second, and third either three or four times. GitHub account concurrency limits may queue jobs.

Both matrices use `fail-fast: false`, allowing the remaining jobs to finish when one fails. A failure within a paired job also does not prevent its later scenarios from running. This does not accept a partial run: the final report waits for all ten paired jobs and all three serial jobs. It accepts the thirty range summaries only when every shard is valid, then adds the three validated serial summaries to the visible GitHub Actions report in Atto, Nano, RSNano order. A missing or invalid paired shard prevents the range outputs. A serial download or reporting failure leaves the workflow red without a complete visible report, although paired range artifacts may still be retained for diagnosis. Artifacts are retained for five days; scheduled runs do not replace accepted results automatically.

## Acceptance and promotion

Do not replace an existing result with a smoke, partial, or failed run. A promoted scenario must contain exactly 1,000 clean serial samples or 50,000 clean 500 × 100 samples, with zero errors, and must retain its CSV, summary, and manifest plus the verified fixture files. The six published artifact triplets are linked from the [`results/` index](../results/README.md).
