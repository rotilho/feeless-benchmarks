# RSNano V3.1 target notes

The suite pins the official [`RSNano V3.1`](https://github.com/rsnano-node/rsnano-node/releases/tag/V3.1) release:

- commit `267e45a5555039d79dba3699c27c574926940681`;
- image `rsnano/rsnano:V3.1`; and
- BSD-3-Clause license.

Resolved image identities are recorded per Kotlin run in its additive manifest. Useful source references remain in [`provenance/source-revisions.json`](../provenance/source-revisions.json).

## Shared Nano specification

RSNano is an independent Rust implementation of the Nano protocol, so it remains an `implementation = "rsnano"` variant of `NanoNodeSpec` in the `nano` module. It consumes the exact Nano V28.2 schema-v2 `dev` fixtures without regeneration or re-signing and uses the same Ktor RPC/WebSocket adapter as Nano.

For each measured block, the adapter:

1. registers the predicted hash before RPC `process`;
2. requires `process` to return that same hash;
3. waits under the same timeout for a post-cement WebSocket event for that exact hash; and
4. returns so `BenchmarkEngine` can take the second monotonic timestamp.

RPC admission alone is not completion. No node election timestamp is part of the result schema or latency calculation.

## Fresh durable environment

Testcontainers creates a new RSNano environment for each scenario. Startup verifies the V3.1 vendor/build, `dev` network identifier, and canonical dev genesis frontier, then installs the public dev genesis voting key. Configured peers and bootstrap paths are disabled for the controlled local measurement.

The node uses a fresh on-disk LMDB data path. RSNano V3.1 defaults to `nosync_unsafe`; the harness overrides it to `sync = "always"` to match the durable Nano profile. No published run uses tmpfs. Wallet background work is unnecessary because the fixture already contains deterministic proof-of-work.

RSNano may still differ from Nano in schedulers, queues, database libraries, thread counts, consensus machinery, and supported RPCs. Results compare only the pinned local binaries on the exercised fixture and completion path; they do not establish general implementation parity or public-network capacity.
