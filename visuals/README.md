# Visualization source

`benchmark-table.html` is a self-contained 1200 × 675 benchmark range table.

It shows the minimum and maximum run-level values across ten accepted 500-account runs per implementation on one dedicated computer, one run at a time. Every run contains 500 independent account chains, 100 transactions per account, 50,000 successful samples, and zero errors.

The exact aggregate, including all thirty summaries and manifests, is available as [JSON](../results/common-runner/500-account-ranges.json) and [Markdown](../results/common-runner/500-account-ranges.md).

Nano and RSNano require the exact post-cement event; Atto requires the matching transaction response. Prior checks found that waiting for cement added about 15 ms to P50 versus confirmation-only timing. That delta provides context for the completion boundary and is not measured by this benchmark.

The table reports AVG, P50, P90, P95, and P99 latency ranges rounded to whole milliseconds, average TPS ranges to two decimal places, and peak TPS ranges as integers. Exact values remain in the aggregate JSON.

Open the HTML directly or serve this directory with a static server. No screenshot, PNG, or export is included or generated.
