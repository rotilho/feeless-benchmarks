# Result data dictionary

The Kotlin runner preserves the existing raw CSV and summary JSON schemas for every implementation. Provenance is additive in a separate manifest.

## Raw CSV

Each row represents one attempted measured prebuilt item.

| Column | Meaning |
| --- | --- |
| `implementation` | Implementation identifier: `atto`, `nano`, or `rsnano`. |
| `fixture` | Canonical fixture name. |
| `lane` | Account-lane identifier. |
| `sequence` | Item order within the lane. |
| `account` | Source account/public key. |
| `hash` | Predicted identifier matched against publication and external completion. |
| `start_monotonic_ns` | `BenchmarkEngine` timestamp immediately before adapter publication. |
| `completion_monotonic_ns` | Engine timestamp immediately after exact external completion; blank on error. |
| `latency_ns` | `completion_monotonic_ns - start_monotonic_ns`; blank on error. |
| `error` | Blank on success; exception type and detail on publication failure. |

A publication failure stops only its lane. Coroutine cancellation is not written as an error row; it propagates to the caller. The schema intentionally contains no node election timestamps, confirmation/cement timing fields, or metrics-system aggregates.

## Summary JSON

| Field | Meaning |
| --- | --- |
| `sample_count` | Total raw rows written, including failed samples. |
| `success_count` | Rows with completion and latency and no error. |
| `error_count` | Rows with a recorded error. |
| `elapsed_ns` | Earliest successful start to latest successful completion; null with no successes. |
| `average_tps` | Successful completions divided by `elapsed_ns`; null when elapsed time is unavailable or zero. |
| `peak_tps` | Maximum successful completions in a half-open one-second sliding completion window. |
| `latency_ns.count` | Number of successful latency values. |
| `latency_ns.sum` | Sum of successful latency values. |
| `latency_ns.min`, `max`, `average` | Minimum, maximum, and arithmetic mean latency. |
| `latency_ns.p50`, `p90`, `p95`, `p99` | Nearest-rank latency percentiles. |

The manifest does not change this schema, so existing summaries remain directly recalculable from their raw CSVs.

## Run manifest JSON

Every new Kotlin result adds `<scenario>-manifest.json` alongside `<scenario>-samples.csv` and `<scenario>-summary.json`.

| Field | Meaning |
| --- | --- |
| `runner_revision` | Git revision of the runner, suffixed with `-dirty` when applicable. |
| `fixture_hashes` | SHA-256 keyed by fixture filename. |
| `image_digests` | Resolved node and database image identities keyed by pinned image reference. |
| `java` | Java version/vendor and VM name/version. |
| `operating_system` | Host OS name, version, and architecture. |
| `cpu` | Logical processor count and host CPU model when available. |
| `storage_profile` | Published profile; currently `durable`. |
| `runtime_configuration` | Sorted implementation/runtime settings with credential-, password-, private-, secret-, and token-like values redacted. |

## Cross-run range JSON

`aggregate-results` writes `<account-count>-account-ranges.json` with schema `feeless-benchmark-summary-aggregate/v1`; the current workflow produces `500-account-ranges.json`. Each implementation retains every source summary and manifest, plus run-level ranges for the summary metrics. A range records `minimum`, `maximum`, `best`, `worst`, `direction`, and `unit`; lower latency and elapsed time are better, while higher average and peak TPS are better. The aggregator requires the complete clean shard count and matching provenance before writing either range output.

Accepted files are indexed in [`results/README.md`](../results/README.md).
