# Results

These are controlled-local results, not protocol limits or mainnet-capacity claims. Latency is rounded to the nearest whole millisecond for display; the linked summaries and raw CSVs retain the authoritative nanosecond values.

| Workload | Scenario | Average (ms) | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Average TPS | Peak TPS | Artifacts |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 × 1,000 serial | Nano V28.2 (`nano-serial`) | 200 | 200 | 205 | 205 | 209 | 4.99 | 6 | [CSV](../results/common-runner/nano-serial/nano-serial-samples.csv) · [summary](../results/common-runner/nano-serial/nano-serial-summary.json) · [manifest](../results/common-runner/nano-serial/nano-serial-manifest.json) |
| 1 × 1,000 serial | Atto 1.34 (`atto-serial`) | 16 | 16 | 21 | 23 | 28 | 60.67 | 71 | [CSV](../results/common-runner/atto-serial/atto-serial-samples.csv) · [summary](../results/common-runner/atto-serial/atto-serial-summary.json) · [manifest](../results/common-runner/atto-serial/atto-serial-manifest.json) |
| 1 × 1,000 serial | RSNano V3.1 (`rsnano-serial`) | 200 | 200 | 202 | 204 | 207 | 4.99 | 6 | [CSV](../results/common-runner/rsnano-serial/rsnano-serial-samples.csv) · [summary](../results/common-runner/rsnano-serial/rsnano-serial-summary.json) · [manifest](../results/common-runner/rsnano-serial/rsnano-serial-manifest.json) |
| 500 × 100 | Nano V28.2 (`nano-500`) | 906 | 905 | 1,036 | 1,056 | 1,136 | 548.45 | 755 | [CSV](../results/common-runner/nano-500/nano-500-samples.csv) · [summary](../results/common-runner/nano-500/nano-500-summary.json) · [manifest](../results/common-runner/nano-500/nano-500-manifest.json) |
| 500 × 100 | Atto 1.34 (`atto-500`) | 160 | 137 | 232 | 276 | 362 | 3,109.91 | 4,000 | [CSV](../results/common-runner/atto-500/atto-500-samples.csv) · [summary](../results/common-runner/atto-500/atto-500-summary.json) · [manifest](../results/common-runner/atto-500/atto-500-manifest.json) |
| 500 × 100 | RSNano V3.1 (`rsnano-500`) | 795 | 767 | 1,226 | 1,385 | 1,661 | 624.74 | 997 | [CSV](../results/common-runner/rsnano-500/rsnano-500-samples.csv) · [summary](../results/common-runner/rsnano-500/rsnano-500-summary.json) · [manifest](../results/common-runner/rsnano-500/rsnano-500-manifest.json) |

The serial summaries each report 1,000 samples and the 500 × 100 summaries each report 50,000. All samples succeeded and every error count is zero. Average TPS is shown to two decimal places and peak TPS as an integer; exact values remain in the summaries.

## Scenario and fixture names

Nano and Atto use matching `*-serial` and `*-500` scenario, artifact, and fixture stems. RSNano uses `rsnano-serial` and `rsnano-500` for its scenarios and artifacts, but consumes `nano-serial` and `nano-500` because both Nano implementations process the same signed blocks.

## Interpretation

The engine starts its monotonic timer immediately before publication and stops it only after exact external completion. Nano and RSNano require an exact-hash post-cement WebSocket event. Atto requires the stream response to return the matching transaction. RPC/HTTP admission and node timestamps are excluded.

A separate confirmation-only comparison indicated a p50 about 15 ms lower than waiting through cementing. That estimate is context for the completion-boundary choice; it is not calculated from these six summaries.

The 500 × 100 workload lets independent account lanes progress concurrently while keeping every account's 100 dependent items serial. Exact values remain host-, storage-, fixture-, and implementation-specific.

For timing and interpretation details, see [Methodology](methodology.md).
