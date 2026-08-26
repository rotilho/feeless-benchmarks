# Contributing

Preserve the shared measurement contract and keep implementation-specific behavior outside `core`.

## Module boundaries

- `core` owns typed benchmark items/scenarios, the engine, raw samples, statistics, CSV, summary JSON, and manifests. It must not depend on Ktor, Testcontainers, or cryptocurrency libraries.
- `nano` owns Nano protocol primitives, offline fixtures, the shared Nano/RSNano transport, and `NanoNodeSpec`.
- `atto` owns Atto fixtures, transport, and its node/database environment.
- `app` is the only executable and owns CLI orchestration, overwrite protection, fresh environments, and serial suites.

All authored application, fixture-generation, and test code must be Kotlin. The generated Gradle wrapper scripts are the only non-Kotlin source exception. Do not add a second runner, nested build, shell orchestrator, or Python package.

## Benchmark rules

- Construct, sign, work, encode, and validate fixtures before timing. Node/database startup and setup publication are also unmeasured.
- Keep `System.nanoTime()` calls inside `BenchmarkEngine`. A successful sample reads the clock exactly twice.
- End a sample only after the adapter observes the exact submitted item's external completion signal. RPC/HTTP admission and node-provided timestamps are not completion.
- Keep items serial within an account lane; independent lanes may run concurrently.
- Stop only the failing lane, recording a null completion and latency. Never convert coroutine cancellation into a sample error.
- Run scenarios strictly serially and create a fresh Testcontainers environment for each one.
- Retain exact raw rows as the authority. Compute summaries with arithmetic mean, nearest-rank percentiles, and the half-open one-second peak-completion window. Do not add Micrometer to the result path.
- Refuse output overwrites. Accept a published scenario only when its raw, successful, and latency counts all match the fixture's declared count, with zero errors. The current counts are 1,000 for serial and 50,000 for 500 × 100.
- Keep the existing CSV and summary JSON schemas compatible. Provenance belongs in the additive manifest.
- Pin official images and dependencies. Published results use the durable storage profile only; do not add tmpfs or durability-reducing database settings.

Benchmark fixtures and keys are disposable local/dev-network material and must never protect live funds.

## Checks

Fast checks do not start containers:

```bash
./gradlew check
```

Run container smoke tests separately on a host with a Docker-compatible API:

```bash
./gradlew containerIntegrationTest
```

CI runs both commands in separate jobs. Focused tasks are also available, for example `./gradlew :core:test`, `./gradlew :nano:test`, and `./gradlew :atto:test`.

Validate canonical fixtures before proposing fixture or result changes:

```bash
./gradlew :app:run --args='validate-fixtures'
```

The generator performs two isolated generations, requires byte-identical outputs, validates them, and only then atomically replaces each canonical file:

```bash
./gradlew :app:run --args='generate-fixtures --implementation=all'
```

## Result contributions

Include the exact CLI command, raw CSV, compatible summary JSON, additive manifest, fixture verification artifacts, official implementation/version pins, resolved image digests, and enough host/configuration context to interpret the controlled-local result. Reviewers must be able to recompute every aggregate from raw rows.

Do not replace an existing result until its corresponding full rerun satisfies the fixture-count and zero-error acceptance rule. A failed or partial run may be retained as diagnostic evidence outside the accepted result paths, but must not be presented as an accepted benchmark.
