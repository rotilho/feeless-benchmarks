# Results

The accepted result set comes from [GitHub Actions run 33016888792](https://github.com/rotilho/feeless-benchmarks/actions/runs/33016888792) and contains six common-runner scenarios plus a ten-run aggregate for the 500 × 100 workload. Every serial summary reports `sample_count = 1000`, `success_count = 1000`, and `error_count = 0`. Every individual 500 × 100 summary reports `sample_count = 50000`, `success_count = 50000`, and `error_count = 0`.

The aggregate embeds the summaries and manifests from ten clean paired jobs on ten GitHub-hosted VMs. Each job ran all three implementations sequentially on the same VM, with a fresh environment for every scenario:

- [`500-account-ranges.json`](common-runner/500-account-ranges.json) retains exact run-level values and provenance.
- [`500-account-ranges.md`](common-runner/500-account-ranges.md) presents latency ranges in whole milliseconds.

| Scenario | Workload | Raw samples | Summary | Manifest |
| --- | --- | --- | --- | --- |
| `nano-serial` — Nano V28.2 | 1 × 1,000 serial | [`nano-serial-samples.csv`](common-runner/nano-serial/nano-serial-samples.csv) | [`nano-serial-summary.json`](common-runner/nano-serial/nano-serial-summary.json) | [`nano-serial-manifest.json`](common-runner/nano-serial/nano-serial-manifest.json) |
| `atto-serial` — Atto 1.34 | 1 × 1,000 serial | [`atto-serial-samples.csv`](common-runner/atto-serial/atto-serial-samples.csv) | [`atto-serial-summary.json`](common-runner/atto-serial/atto-serial-summary.json) | [`atto-serial-manifest.json`](common-runner/atto-serial/atto-serial-manifest.json) |
| `rsnano-serial` — RSNano V3.1 | 1 × 1,000 serial | [`rsnano-serial-samples.csv`](common-runner/rsnano-serial/rsnano-serial-samples.csv) | [`rsnano-serial-summary.json`](common-runner/rsnano-serial/rsnano-serial-summary.json) | [`rsnano-serial-manifest.json`](common-runner/rsnano-serial/rsnano-serial-manifest.json) |
| `nano-500` — Nano V28.2 | 500 × 100 | [`nano-500-samples.csv`](common-runner/nano-500/nano-500-samples.csv) | [`nano-500-summary.json`](common-runner/nano-500/nano-500-summary.json) | [`nano-500-manifest.json`](common-runner/nano-500/nano-500-manifest.json) |
| `atto-500` — Atto 1.34 | 500 × 100 | [`atto-500-samples.csv`](common-runner/atto-500/atto-500-samples.csv) | [`atto-500-summary.json`](common-runner/atto-500/atto-500-summary.json) | [`atto-500-manifest.json`](common-runner/atto-500/atto-500-manifest.json) |
| `rsnano-500` — RSNano V3.1 | 500 × 100 | [`rsnano-500-samples.csv`](common-runner/rsnano-500/rsnano-500-samples.csv) | [`rsnano-500-summary.json`](common-runner/rsnano-500/rsnano-500-summary.json) | [`rsnano-500-manifest.json`](common-runner/rsnano-500/rsnano-500-manifest.json) |

The scenario name is also its published directory and file stem. The published 500 × 100 single-run artifacts come from paired run 01. RSNano uses the same `nano-serial` and `nano-500` fixture files as Nano. Each manifest records fixture hashes, resolved image digests, runner revision, host/runtime details, the durable storage profile, and sanitized configuration.

Use a fresh output root for reproduction work:

```bash
./gradlew :app:run --args='run-suite --implementations=nano,atto,rsnano --output-root=results/reproduction/full-suite'
```

The command refuses an existing output root and executes all scenarios serially on fresh Testcontainers environments. A promoted current result must preserve all three artifacts and contain exactly 1,000 clean serial samples or 50,000 clean 500 × 100 samples, with zero errors. Do not write exploratory or partial runs over accepted result paths.
