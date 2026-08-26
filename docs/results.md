# Results

These results come from [GitHub Actions run 33016888792](https://github.com/rotilho/feeless-benchmarks/actions/runs/33016888792); they are not protocol limits or mainnet-capacity claims. Latency is rounded to the nearest whole millisecond for display; the linked summaries and raw CSVs retain the authoritative nanosecond values.

## Ten-run 500-account ranges

Each cell is the minimum-to-maximum run-level value across ten complete runs on ten GitHub-hosted VMs. Within each paired job, Atto, Nano, and RSNano ran sequentially on the same VM, with a fresh durable environment for every scenario. The 500,000 measured samples per implementation are not pooled before calculating these ranges. Every run completed 50,000 samples and reported zero errors.

| Scenario | Runs | Average (ms) ↓ | p50 (ms) ↓ | p90 (ms) ↓ | p95 (ms) ↓ | p99 (ms) ↓ | Average TPS ↑ | Peak TPS ↑ |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Nano V28.2 (`nano-500`) | 10 | 367–814 | 368–684 | 436–1,580 | 496–1,794 | 632–2,349 | 603.37–1,320.84 | 1,629–1,902 |
| Atto 1.34 (`atto-500`) | 10 | 322–444 | 291–416 | 508–642 | 611–747 | 743–1,093 | 1,104.05–1,513.81 | 1,425–2,534 |
| RSNano V3.1 (`rsnano-500`) | 10 | 662–1,154 | 696–1,090 | 908–1,807 | 1,006–2,057 | 1,109–2,536 | 429.99–749.87 | 851–1,145 |

The exact aggregate, including every source summary and manifest, is available as [JSON](../results/common-runner/500-account-ranges.json) and [Markdown](../results/common-runner/500-account-ranges.md).

## Single-run artifacts

| Workload | Scenario | Average (ms) | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Average TPS | Peak TPS | Artifacts |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 × 1,000 serial | Nano V28.2 (`nano-serial`) | 199 | 200 | 200 | 200 | 201 | 5.02 | 9 | [CSV](../results/common-runner/nano-serial/nano-serial-samples.csv) · [summary](../results/common-runner/nano-serial/nano-serial-summary.json) · [manifest](../results/common-runner/nano-serial/nano-serial-manifest.json) |
| 1 × 1,000 serial | Atto 1.34 (`atto-serial`) | 22 | 7 | 59 | 98 | 215 | 45.08 | 95 | [CSV](../results/common-runner/atto-serial/atto-serial-samples.csv) · [summary](../results/common-runner/atto-serial/atto-serial-summary.json) · [manifest](../results/common-runner/atto-serial/atto-serial-manifest.json) |
| 1 × 1,000 serial | RSNano V3.1 (`rsnano-serial`) | 201 | 200 | 201 | 202 | 221 | 4.98 | 6 | [CSV](../results/common-runner/rsnano-serial/rsnano-serial-samples.csv) · [summary](../results/common-runner/rsnano-serial/rsnano-serial-summary.json) · [manifest](../results/common-runner/rsnano-serial/rsnano-serial-manifest.json) |
| 500 × 100 | Nano V28.2 (`nano-500`) | 450 | 396 | 694 | 812 | 1,609 | 1,090.57 | 1,838 | [CSV](../results/common-runner/nano-500/nano-500-samples.csv) · [summary](../results/common-runner/nano-500/nano-500-summary.json) · [manifest](../results/common-runner/nano-500/nano-500-manifest.json) |
| 500 × 100 | Atto 1.34 (`atto-500`) | 322 | 291 | 508 | 611 | 743 | 1,513.81 | 2,178 | [CSV](../results/common-runner/atto-500/atto-500-samples.csv) · [summary](../results/common-runner/atto-500/atto-500-summary.json) · [manifest](../results/common-runner/atto-500/atto-500-manifest.json) |
| 500 × 100 | RSNano V3.1 (`rsnano-500`) | 1,063 | 980 | 1,794 | 2,057 | 2,536 | 466.60 | 1,109 | [CSV](../results/common-runner/rsnano-500/rsnano-500-samples.csv) · [summary](../results/common-runner/rsnano-500/rsnano-500-summary.json) · [manifest](../results/common-runner/rsnano-500/rsnano-500-manifest.json) |

The serial summaries each report 1,000 samples. The representative 500 × 100 summaries are from paired run 01 and each report 50,000 samples. All samples succeeded and every error count is zero. Average TPS is shown to two decimal places and peak TPS as an integer; exact values remain in the summaries.

## Scenario and fixture names

Nano and Atto use matching `*-serial` and `*-500` scenario, artifact, and fixture stems. RSNano uses `rsnano-serial` and `rsnano-500` for its scenarios and artifacts, but consumes `nano-serial` and `nano-500` because both Nano implementations process the same signed blocks.

## Interpretation

The engine starts its monotonic timer immediately before publication and stops it only after exact external completion. Nano and RSNano require an exact-hash post-cement WebSocket event. Atto requires the stream response to return the matching transaction. RPC/HTTP admission and node timestamps are excluded.

A separate confirmation-only comparison indicated a p50 about 15 ms lower than waiting through cementing. That estimate is context for the completion-boundary choice; it is not calculated from these six summaries.

The 500 × 100 workload lets independent account lanes progress concurrently while keeping every account's 100 dependent items serial. Exact values remain host-, storage-, fixture-, and implementation-specific.

For timing and interpretation details, see [Methodology](methodology.md).
