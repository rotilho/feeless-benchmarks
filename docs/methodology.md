# Methodology

This suite measures pinned implementations in isolated single-host environments with one voter. Paired workflow runs place all three implementations on the same host within each repetition. It does not measure a distributed production network or establish mainnet capacity.

## Workloads and controls

| Workload | Shape | Measured items |
| --- | --- | ---: |
| Serial | 1 account lane × 1,000 dependent items | 1,000 |
| Independent lanes | 500 account lanes × 100 dependent items | 50,000 |

Scenarios sharing a paired job run one after another and never overlap; separate workflow jobs may run concurrently on different VMs. Every measured block or transaction is constructed, signed, worked, and validated before measurement. Atto transaction bodies are also encoded before measurement; Nano's shared adapter encodes the RPC envelope during measured publication. Fixture generation, node/database startup, account/ledger setup, and setup publication are outside the timed path.

Fixture construction advances one item per account per sequence round. For Atto, every account shares the round timestamp and each account's next item advances by one millisecond. The recorded `workSearchParallelism = 1` applies independently to each account lane; lanes themselves are generated concurrently.

`app` creates a fresh Testcontainers environment for every scenario. Nano V28.2 and RSNano V3.1 consume the same offline-generated Nano schema-v2 fixtures. Each uses the `dev` network, a fresh on-disk LMDB data path, and the canonical public dev voting key. RSNano explicitly uses `sync = "always"`. Atto uses one official `1.34-live` voter and MySQL 8.4 with its persistent defaults. Published scenarios never use tmpfs or durability-reducing flags.

## Engine and timer contract

`core` contains no implementation or transport dependency. `BenchmarkEngine` is the only code allowed to call `System.nanoTime()`:

1. It publishes setup without reading the clock.
2. It starts independent lanes concurrently and keeps each lane serial.
3. Immediately before one measured adapter call, it reads the monotonic clock once.
4. After the adapter returns from exact external completion, it reads the clock once more.
5. If publication fails, it records null completion/latency and stops only that lane.
6. It propagates coroutine cancellation rather than recording it as a sample error.

RPC or HTTP admission alone is not success. Node-provided processing, election, or cement timestamps are not used.

The CLI caller, account lanes, HTTP/WebSocket clients, Nano/RSNano confirmation listener, and coroutine-wrapped container calls use one process-wide Java 21 virtual-thread dispatcher. Lane ordering and concurrency remain owned by `BenchmarkEngine`, and each run records `coroutine.dispatcher=virtual` in its manifest.

## Completion adapters

### Nano and RSNano

Both implementations use the same Ktor RPC/WebSocket adapter and `NanoNodeSpec`. Before RPC `process`, the adapter registers the predicted block hash so an early notification cannot be lost. One timeout covers the RPC and confirmation wait. The RPC must return the predicted hash, and the WebSocket notification must be an exact-hash post-cement event.

Startup verifies the pinned vendor/build, `dev` network identifier, and canonical genesis frontier before installing the public dev voting key. RSNano remains a Nano node specification, rather than a separate protocol runner, because the fixture and completion semantics are shared.

### Atto

Each measured item already contains its LF-free JSON object bytes. The adapter sends them exactly once to `POST /transactions/stream`. It requires:

- HTTP status 200;
- media type `application/x-ndjson`;
- exactly one nonblank, LF-terminated JSON object;
- a valid decoded transaction; and
- a transaction hash matching the submitted item.

There are no retries, GET/database fallbacks, Prometheus scraping, or alternate result runners.

## Raw output and statistics

Each completed standalone run writes one raw CSV row per attempted measured item. Those rows are authoritative; the runner does not use Micrometer. If a standalone run is partial, it writes diagnostic CSV, summary, and manifest artifacts before exiting unsuccessfully. A current suite scenario is written and accepted only with exactly 1,000 serial samples or 50,000 independent-lane samples, the same number of successes, and zero errors.

Latency statistics use successful `latency_ns` values. Average is arithmetic mean; p50, p90, p95, and p99 use nearest rank, `sorted[ceil(p × n) - 1]`. Average TPS is successful completions divided by elapsed measured time. Peak TPS is the maximum completions in a half-open one-second sliding completion window.

The CSV and summary JSON schemas remain stable across scenarios. Each run also writes an additive manifest containing the runner revision, fixture SHA-256 values, resolved image digests, Java/OS/CPU details, the durable storage profile, and sanitized runtime configuration.

See the [results](results.md), [data dictionary](data-dictionary.md), and [reproduction guide](reproduction.md).

## Limits

The single host, one local voter, dev/local networks, synthetic fixtures, and implementation-specific storage paths intentionally remove many production effects. Results describe only these pinned local binaries, fixtures, storage profile, and completion contract.
