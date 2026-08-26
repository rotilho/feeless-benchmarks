package dev.feeless.benchmarks.core

import kotlin.math.ceil

object BenchmarkStatistics {
    fun nearestRank(
        values: Iterable<Long>,
        percentile: Double,
    ): Long {
        require(percentile in 0.0..100.0) { "percentile must be between 0 and 100" }
        val ordered = values.sorted()
        require(ordered.isNotEmpty()) { "values must not be empty" }

        val rank = ceil(percentile / 100.0 * ordered.size).toInt().coerceAtLeast(1)
        return ordered[rank - 1]
    }

    fun summarizeLatencies(values: Iterable<Long>): LatencySummary {
        val ordered = values.sorted()
        if (ordered.isEmpty()) {
            return LatencySummary(
                average = null,
                count = 0,
                max = null,
                min = null,
                p50 = null,
                p90 = null,
                p95 = null,
                p99 = null,
                sum = 0,
            )
        }

        val sum = ordered.sum()
        return LatencySummary(
            average = sum.toDouble() / ordered.size,
            count = ordered.size,
            max = ordered.last(),
            min = ordered.first(),
            p50 = nearestRank(ordered, 50.0),
            p90 = nearestRank(ordered, 90.0),
            p95 = nearestRank(ordered, 95.0),
            p99 = nearestRank(ordered, 99.0),
            sum = sum,
        )
    }

    fun peakOneSecondCompletions(completionNs: Iterable<Long>): Int {
        val ordered = completionNs.sorted()
        var left = 0
        var peak = 0
        ordered.forEachIndexed { right, completion ->
            while (completion - ordered[left] >= ONE_SECOND_NS) {
                left += 1
            }
            peak = maxOf(peak, right - left + 1)
        }
        return peak
    }

    fun summarizeThroughput(
        startNs: Iterable<Long>,
        completionNs: Iterable<Long>,
    ): ThroughputSummary {
        val starts = startNs.toList()
        val completions = completionNs.toList()
        require(starts.size == completions.size) {
            "start and completion timestamps must have the same count"
        }
        if (starts.isEmpty()) {
            return ThroughputSummary(elapsedNs = null, averageTps = null, peakTps = 0)
        }

        val elapsedNs = completions.max() - starts.min()
        val averageTps =
            if (elapsedNs > 0) {
                completions.size * ONE_SECOND_NS.toDouble() / elapsedNs
            } else {
                null
            }
        return ThroughputSummary(
            elapsedNs = elapsedNs,
            averageTps = averageTps,
            peakTps = peakOneSecondCompletions(completions),
        )
    }

    fun summarize(samples: Iterable<BenchmarkSample>): BenchmarkSummary {
        val materialized = samples.toList()
        val successful =
            materialized.filter { sample ->
                sample.error == null &&
                    sample.completionMonotonicNs != null &&
                    sample.latencyNs != null
            }
        val throughput =
            summarizeThroughput(
                startNs = successful.map(BenchmarkSample::startMonotonicNs),
                completionNs = successful.map { sample -> requireNotNull(sample.completionMonotonicNs) },
            )

        return BenchmarkSummary(
            averageTps = throughput.averageTps,
            elapsedNs = throughput.elapsedNs,
            errorCount = materialized.count { sample -> sample.error != null },
            latencyNs = summarizeLatencies(successful.map { sample -> requireNotNull(sample.latencyNs) }),
            peakTps = throughput.peakTps,
            sampleCount = materialized.size,
            successCount = successful.size,
        )
    }

    private const val ONE_SECOND_NS = 1_000_000_000L
}
