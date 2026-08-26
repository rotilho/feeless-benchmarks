# Benchmark specification

## Scenarios

| Scenario | Source lanes | Measured items per lane | Total measured items |
| --- | ---: | ---: | ---: |
| Serial | 1 | 1,000 | 1,000 |
| Independent account chains | 500 | 100 | 50,000 |

Before timing, construct and validate all setup and measured items, including signatures and proof-of-work. Fixture construction advances every account once per sequence round. Atto gives every account the same timestamp within a round and advances it by one millisecond for the next round, so timestamps increase within each account without creating an account-specific priority range. Atto request bodies are also serialized before timing; Nano's RPC envelope is serialized by the shared adapter inside the measured publication boundary. Start a fresh isolated node/database environment and complete required setup publication. Every suite scenario runs serially on a new Testcontainers environment using durable storage.

## Typed execution contract

`core` exposes generic benchmark items and scenarios plus a suspending publication adapter. It does not know Ktor, Testcontainers, Nano, RSNano, or Atto.

```kotlin
data class BenchmarkItem<P>(val lane: String, val sequence: Int, val account: String, val hash: String, val payload: P)
data class BenchmarkScenario<P>(val implementation: String, val fixture: String, val setup: List<BenchmarkItem<P>>, val lanes: Map<String, List<BenchmarkItem<P>>>, val expectedCount: Int)

fun interface PublishAdapter<P> {
    suspend fun publish(item: BenchmarkItem<P>, timeout: Duration)
}
```

`BenchmarkEngine` exclusively owns `System.nanoTime()` and enforces these invariants:

- setup publication is unmeasured;
- lanes run concurrently, while items within each lane run serially;
- success reads the clock exactly twice: immediately before adapter publication and immediately after exact completion;
- a publication failure records one sample with null completion/latency and stops only that lane; and
- coroutine cancellation propagates instead of becoming a sample error.

## Measured boundary

```text
System.nanoTime immediately before adapter publish
→ submit one fully prepared item
→ observe that exact item's external completion signal
→ adapter returns
→ System.nanoTime immediately after return
```

RPC or HTTP admission alone is not success. Node-provided election or processing timestamps are not used.

- Nano and RSNano register the predicted hash before RPC `process`, require the RPC response hash, and wait under the same timeout for the exact post-cement WebSocket event.
- Atto performs one `POST /transactions/stream`, requires status 200, `application/x-ndjson`, exactly one LF-terminated transaction object, and the submitted transaction hash. There are no retries, polling, database fallback, or metrics-scraping completion paths.

## Required outputs

Each scenario writes three files without overwriting existing paths:

- one raw CSV row per attempted measured item using the compatible common schema;
- a summary JSON using the compatible count, latency, and throughput schema; and
- an additive manifest with runner revision, fixture SHA-256 values, resolved image digests, Java/OS/CPU details, storage profile, and sanitized runtime configuration.

Exact raw rows are authoritative. Summary latency values use successful samples only. Percentiles use nearest rank, `sorted[ceil(p × n) - 1]`; peak TPS is the maximum number of successful completions in a half-open one-second window. No Micrometer aggregate is accepted as result evidence.

An accepted current scenario has exactly its declared raw sample count—1,000 for serial or 50,000 for 500 × 100—the same success count, and zero errors. A local result describes the tested implementation, version, configuration, storage engine, host, and topology. It is not a protocol limit or public-network capacity estimate.
