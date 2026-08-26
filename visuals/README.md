# Visualization source

`benchmark-table.html` is a self-contained 1200 × 675 benchmark table.

It shows the published 500-account workload: 500 independent account chains, 100 transactions per account, 50,000 successful samples per implementation, and zero errors.

- `nano-500` — Nano V28.2: [CSV](../results/common-runner/nano-500/nano-500-samples.csv), [summary](../results/common-runner/nano-500/nano-500-summary.json), [manifest](../results/common-runner/nano-500/nano-500-manifest.json).
- `atto-500` — Atto 1.34: [CSV](../results/common-runner/atto-500/atto-500-samples.csv), [summary](../results/common-runner/atto-500/atto-500-summary.json), [manifest](../results/common-runner/atto-500/atto-500-manifest.json).
- `rsnano-500` — RSNano V3.1: [CSV](../results/common-runner/rsnano-500/rsnano-500-samples.csv), [summary](../results/common-runner/rsnano-500/rsnano-500-summary.json), [manifest](../results/common-runner/rsnano-500/rsnano-500-manifest.json).

Nano and RSNano require the exact post-cement event; Atto requires the matching transaction response. Prior checks found that waiting for cement added about 15 ms to P50 versus confirmation-only timing. That delta provides context for the completion boundary and is not measured by this benchmark.

The table reports AVG, P50, P90, P95, and P99 latency rounded to whole milliseconds, average TPS to two decimal places, and peak TPS as an integer. Exact nanosecond latency and unrounded TPS values remain in the linked summaries and raw CSVs.

Open the HTML directly or serve this directory with a static server. No screenshot, PNG, or export is included or generated.
