# RSNano runtime and fixture notes

New runs use the official moving image reference `rsnano/rsnano:latest`. The runner pulls it for each scenario by default and checks that the node reports an `RsNano V` vendor, the `dev` network, and the canonical dev genesis frontier. Each manifest records the configured image reference and resolved digest.

The accepted V3.1 results used the official [`RSNano V3.1`](https://github.com/rsnano-node/rsnano-node/releases/tag/V3.1) image at commit `267e45a5555039d79dba3699c27c574926940681`. Their manifests preserve the exact image digests. RSNano remains BSD-3-Clause licensed.

Useful source references remain in [`provenance/source-revisions.json`](../provenance/source-revisions.json).

## Shared Nano specification

RSNano is an independent Rust implementation of the Nano protocol, so it remains an `implementation = "rsnano"` variant of `NanoNodeSpec` in the `nano` module. It consumes the exact Nano V28.2 schema-v2 `dev` fixtures without regeneration or re-signing and uses the same Ktor RPC/WebSocket adapter as Nano. Those fixture schema and source-version pins describe fixture generation; they do not pin the current RSNano runtime image.

For each measured block, the adapter:

1. registers the predicted hash before RPC `process`;
2. requires `process` to return that same hash;
3. waits under the same timeout for a post-cement WebSocket event for that exact hash; and
4. returns so `BenchmarkEngine` can take the second monotonic timestamp.

RPC admission alone is not completion. No node election timestamp is part of the result schema or latency calculation.

## Fresh durable environment

Testcontainers creates a new RSNano environment for each scenario. Startup verifies the RSNano vendor family, `dev` network identifier, and canonical dev genesis frontier, then installs the public dev genesis voting key. Configured peers and bootstrap paths are disabled for the controlled local measurement.

The node uses a fresh on-disk LMDB data path and the harness sets `sync = "always"` to match the durable Nano profile. No published run uses tmpfs. Wallet background work is unnecessary because the fixture already contains deterministic proof-of-work.

RSNano may still differ from Nano in schedulers, queues, database libraries, thread counts, consensus machinery, and supported RPCs. Results compare only the local binaries identified by their manifest image digests on the exercised fixture and completion path; they do not establish general implementation parity or public-network capacity.
