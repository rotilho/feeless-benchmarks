# Frequently asked questions

## Why one benchmark runner?

One engine owns timing, lane behavior, raw output, statistics, overwrite protection, and manifests for every implementation. This keeps the measurement contract common across all six scenarios.

## Was signing or proof-of-work inside the timer?

No. Fixtures contain complete blocks or transactions, so fixture generation, signing, proof-of-work, node/database startup, and account/ledger setup finish before measured publication. Atto request bodies are pre-encoded; Nano's shared adapter encodes its RPC envelope inside the measured publication call.

## Did Nano and RSNano receive the same blocks?

Yes. The generator creates Nano V28.2 `dev`-network fixture schema v2 completely offline. Nano V28.2 and RSNano V3.1 consume those exact signed-and-worked blocks without regeneration or re-signing.

## What does one latency sample measure?

`BenchmarkEngine` calls `System.nanoTime()` immediately before the adapter and again immediately after the adapter returns from the exact external completion signal.

- Nano and RSNano register the predicted hash before RPC `process`, require the returned hash, and wait for the exact post-cement WebSocket event under one timeout.
- Atto sends one pre-encoded transaction to `POST /transactions/stream` and requires the successful response to return that matching transaction.

RPC/HTTP admission alone is never success, and no node timestamp contributes to the sample.

## What happens when a publication fails?

The attempted item receives an error row with null completion and latency. Only that lane stops; other lanes continue. Coroutine cancellation propagates and is not converted into sample data. Standalone `run` writes the partial diagnostics and then exits unsuccessfully; `run-suite` rejects the scenario before writing accepted artifacts.

## Where do the aggregates come from?

The raw CSV rows are authoritative. The runner calculates the summary with arithmetic mean, nearest-rank percentiles, elapsed completion throughput, and a half-open one-second peak-completion window. The suite does not use Micrometer or Prometheus aggregates as benchmark results.

## Why is there a separate manifest?

Keeping provenance additive preserves the established CSV and summary JSON schemas. Each run records the runner revision, fixture SHA-256 values, resolved image digests, Java/OS/CPU details, durable storage profile, and sanitized runtime settings beside those compatible outputs.

## Are the containers ephemeral or durable?

The environment is fresh and disposable per scenario, but the tested storage behavior is durable: on-disk data paths, no tmpfs, MySQL 8.4 defaults for Atto, Nano defaults, and RSNano `sync = "always"`. Testcontainers owns startup and cleanup.

## Are these mainnet-capacity claims?

No. These are isolated single-host measurements with one voter and dev/local ledgers. Within each paired 500-account repetition, all three implementations share one host. Public-network propagation, representative diversity, and adversarial traffic are absent.

## Why can independent account lanes be faster than one serial lane?

Each lane remains serial, but 500 independent account chains can progress concurrently. One 1,000-item account chain exposes no lane-level concurrency.
