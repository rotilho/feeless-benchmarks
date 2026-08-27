# Visualization source

`benchmark-table.html` is a self-contained 1200 × 675 parallel and serial benchmark table.

It shows results from [GitHub Actions run 33041780835](https://github.com/rotilho/feeless-benchmarks/actions/runs/33041780835). The parallel table contains the minimum and maximum values from ten paired jobs on ten GitHub-hosted VMs. Each job ran Atto, Nano, and RSNano sequentially on the same VM, with a fresh environment for every scenario. Each parallel run contains 500 independent account chains, 100 transactions per account, 50,000 successful samples, and zero errors. The serial table contains one 1,000-transaction run per implementation from the same workflow.

The exact parallel aggregate, including all thirty summaries and manifests, and the exact serial results are attached to the workflow run. Its [aggregate job](https://github.com/rotilho/feeless-benchmarks/actions/runs/33041780835/job/98419762656) presents the same values in Markdown.

Nano and RSNano require the exact post-cement event; Atto requires the matching transaction response. Prior checks found that waiting for cement added about 15 ms to P50 versus confirmation-only timing. That delta provides context for the completion boundary and is not measured by this benchmark.

Both tables report AVG, P50, P90, P95, and P99 latency rounded to whole milliseconds, average TPS to two decimal places, and peak TPS as integers. Exact values remain in the source JSON.

Every runner exposed four logical processors. The paired spread is consistent with Atto putting less pressure on storage and benefiting more from CPU performance, while Nano and RSNano appear more sensitive to disk performance. GitHub did not record a disk benchmark, so this is an observed pattern rather than a controlled causal result.

Open the HTML directly or serve this directory with a static server. No screenshot, PNG, or export is included or generated.
