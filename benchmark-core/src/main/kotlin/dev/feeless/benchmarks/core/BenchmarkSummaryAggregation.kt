package dev.feeless.benchmarks.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MetricDirection {
    @SerialName("lower_is_better")
    LOWER_IS_BETTER,

    @SerialName("higher_is_better")
    HIGHER_IS_BETTER,
}

@Serializable
enum class MetricUnit {
    @SerialName("nanoseconds")
    NANOSECONDS,

    @SerialName("transactions_per_second")
    TRANSACTIONS_PER_SECOND,
}

@Serializable
data class LongMetricRange(
    val direction: MetricDirection,
    val unit: MetricUnit,
    val minimum: Long,
    val maximum: Long,
    val best: Long,
    val worst: Long,
)

@Serializable
data class IntMetricRange(
    val direction: MetricDirection,
    val unit: MetricUnit,
    val minimum: Int,
    val maximum: Int,
    val best: Int,
    val worst: Int,
)

@Serializable
data class DoubleMetricRange(
    val direction: MetricDirection,
    val unit: MetricUnit,
    val minimum: Double,
    val maximum: Double,
    val best: Double,
    val worst: Double,
)

@Serializable
data class AggregatedLatencyMetrics(
    val average: DoubleMetricRange,
    val max: LongMetricRange,
    val min: LongMetricRange,
    val p50: LongMetricRange,
    val p90: LongMetricRange,
    val p95: LongMetricRange,
    val p99: LongMetricRange,
    val sum: LongMetricRange,
)

@Serializable
data class AggregatedThroughputMetrics(
    @SerialName("average_tps")
    val averageTps: DoubleMetricRange,
    @SerialName("elapsed_ns")
    val elapsedNs: LongMetricRange,
    @SerialName("peak_tps")
    val peakTps: IntMetricRange,
)

@Serializable
data class AggregatedBenchmarkMetrics(
    @SerialName("latency_ns")
    val latencyNs: AggregatedLatencyMetrics,
    val throughput: AggregatedThroughputMetrics,
)

@Serializable
data class AggregatedBenchmarkRun(
    val source: String,
    val summary: BenchmarkSummary,
    val manifest: RunManifest,
)

@Serializable
data class ImplementationBenchmarkAggregate(
    val implementation: String,
    val scenario: String,
    val fixture: String,
    @SerialName("run_count")
    val runCount: Int,
    val runs: List<AggregatedBenchmarkRun>,
    val metrics: AggregatedBenchmarkMetrics,
)

@Serializable
data class BenchmarkAggregate(
    val schema: String = SCHEMA,
    @SerialName("expected_runs")
    val expectedRuns: Int,
    @SerialName("expected_sample_count")
    val expectedSampleCount: Int,
    val implementations: List<ImplementationBenchmarkAggregate>,
) {
    companion object {
        const val SCHEMA = "feeless-benchmark-summary-aggregate/v1"
    }
}

data class BenchmarkAggregationInput(
    val implementation: String,
    val scenario: String,
    val fixture: String,
    val runs: List<AggregatedBenchmarkRun>,
)

object BenchmarkSummaryAggregator {
    fun aggregate(
        inputs: List<BenchmarkAggregationInput>,
        expectedRuns: Int,
        expectedSampleCount: Int = 10_000,
    ): BenchmarkAggregate {
        require(expectedRuns > 0) { "expected runs must be positive" }
        require(expectedSampleCount > 0) { "expected sample count must be positive" }
        require(inputs.isNotEmpty()) { "at least one implementation is required" }
        require(inputs.map { it.implementation }.distinct().size == inputs.size) {
            "implementations must be unique"
        }

        val implementations =
            inputs.map { input ->
                aggregateImplementation(input, expectedRuns, expectedSampleCount)
            }

        return BenchmarkAggregate(
            expectedRuns = expectedRuns,
            expectedSampleCount = expectedSampleCount,
            implementations = implementations,
        )
    }

    private fun aggregateImplementation(
        input: BenchmarkAggregationInput,
        expectedRuns: Int,
        expectedSampleCount: Int,
    ): ImplementationBenchmarkAggregate {
        require(input.implementation.isNotBlank()) { "implementation must not be blank" }
        require(input.scenario.isNotBlank()) { "${input.implementation}: scenario must not be blank" }
        require(input.fixture.isNotBlank()) { "${input.implementation}: fixture must not be blank" }
        require(input.runs.size == expectedRuns) {
            "${input.implementation} has ${input.runs.size} runs; expected $expectedRuns"
        }
        require(
            input.runs
                .map { it.source }
                .distinct()
                .size == input.runs.size,
        ) {
            "${input.implementation} contains duplicate run sources"
        }

        val runs = input.runs.sortedBy(AggregatedBenchmarkRun::source)
        runs.forEach { run -> validateRun(input, run, expectedSampleCount) }
        validateConsistentProvenance(input.implementation, runs)

        return ImplementationBenchmarkAggregate(
            implementation = input.implementation,
            scenario = input.scenario,
            fixture = input.fixture,
            runCount = runs.size,
            runs = runs,
            metrics = aggregateMetrics(runs),
        )
    }

    private fun validateRun(
        input: BenchmarkAggregationInput,
        run: AggregatedBenchmarkRun,
        expectedSampleCount: Int,
    ) {
        require(run.source.isNotBlank()) { "${input.implementation}: run source must not be blank" }
        val summary = run.summary
        require(summary.sampleCount == expectedSampleCount) {
            "${input.implementation} ${run.source}: sample_count=${summary.sampleCount}; expected $expectedSampleCount"
        }
        require(summary.successCount == expectedSampleCount) {
            "${input.implementation} ${run.source}: success_count=${summary.successCount}; expected $expectedSampleCount"
        }
        require(summary.errorCount == 0) {
            "${input.implementation} ${run.source}: error_count=${summary.errorCount}; expected 0"
        }
        require(summary.latencyNs.count == expectedSampleCount) {
            "${input.implementation} ${run.source}: latency count=${summary.latencyNs.count}; expected $expectedSampleCount"
        }
        validateMetrics(input.implementation, run)
        validateManifestIdentity(input, run)
    }

    private fun validateMetrics(
        implementation: String,
        run: AggregatedBenchmarkRun,
    ) {
        val summary = run.summary
        val latency = summary.latencyNs
        val averageLatency =
            requireNotNull(latency.average) {
                "$implementation ${run.source}: average latency is missing"
            }
        val min = requireNotNull(latency.min) { "$implementation ${run.source}: minimum latency is missing" }
        val max = requireNotNull(latency.max) { "$implementation ${run.source}: maximum latency is missing" }
        val p50 = requireNotNull(latency.p50) { "$implementation ${run.source}: p50 latency is missing" }
        val p90 = requireNotNull(latency.p90) { "$implementation ${run.source}: p90 latency is missing" }
        val p95 = requireNotNull(latency.p95) { "$implementation ${run.source}: p95 latency is missing" }
        val p99 = requireNotNull(latency.p99) { "$implementation ${run.source}: p99 latency is missing" }
        val elapsedNs = requireNotNull(summary.elapsedNs) { "$implementation ${run.source}: elapsed_ns is missing" }
        val averageTps = requireNotNull(summary.averageTps) { "$implementation ${run.source}: average_tps is missing" }

        require(averageLatency.isFinite() && averageLatency >= 0.0) {
            "$implementation ${run.source}: average latency must be finite and nonnegative"
        }
        require(min >= 0 && min <= p50 && p50 <= p90 && p90 <= p95 && p95 <= p99 && p99 <= max) {
            "$implementation ${run.source}: latency metrics are not ordered"
        }
        require(averageLatency in min.toDouble()..max.toDouble()) {
            "$implementation ${run.source}: average latency is outside the minimum and maximum"
        }
        require(latency.sum >= 0) { "$implementation ${run.source}: latency sum must be nonnegative" }
        require(elapsedNs > 0) { "$implementation ${run.source}: elapsed_ns must be positive" }
        require(averageTps.isFinite() && averageTps > 0.0) {
            "$implementation ${run.source}: average_tps must be finite and positive"
        }
        require(summary.peakTps > 0) { "$implementation ${run.source}: peak_tps must be positive" }
    }

    private fun validateManifestIdentity(
        input: BenchmarkAggregationInput,
        run: AggregatedBenchmarkRun,
    ) {
        val manifest = run.manifest
        require(manifest.storageProfile == "durable") {
            "${input.implementation} ${run.source}: storage profile must be durable"
        }
        require(manifest.runtimeConfiguration["implementation"] == input.implementation) {
            "${input.implementation} ${run.source}: manifest implementation does not match"
        }
        require(manifest.runtimeConfiguration["fixture"] == input.fixture) {
            "${input.implementation} ${run.source}: manifest fixture does not match ${input.fixture}"
        }
    }

    private fun validateConsistentProvenance(
        implementation: String,
        runs: List<AggregatedBenchmarkRun>,
    ) {
        val baseline = runs.first().manifest
        runs.drop(1).forEach { run ->
            val manifest = run.manifest
            require(manifest.fixtureHashes == baseline.fixtureHashes) {
                "$implementation ${run.source}: fixture hashes differ from the other runs"
            }
            require(manifest.imageDigests == baseline.imageDigests) {
                "$implementation ${run.source}: image digests differ from the other runs"
            }
            require(manifest.runtimeConfiguration == baseline.runtimeConfiguration) {
                "$implementation ${run.source}: runtime configuration differs from the other runs"
            }
            require(manifest.storageProfile == baseline.storageProfile) {
                "$implementation ${run.source}: storage profile differs from the other runs"
            }
        }
    }

    private fun aggregateMetrics(runs: List<AggregatedBenchmarkRun>): AggregatedBenchmarkMetrics {
        val summaries = runs.map(AggregatedBenchmarkRun::summary)
        return AggregatedBenchmarkMetrics(
            latencyNs =
                AggregatedLatencyMetrics(
                    average = doubleRange(summaries.map { requireNotNull(it.latencyNs.average) }, MetricDirection.LOWER_IS_BETTER),
                    max = longRange(summaries.map { requireNotNull(it.latencyNs.max) }, MetricDirection.LOWER_IS_BETTER),
                    min = longRange(summaries.map { requireNotNull(it.latencyNs.min) }, MetricDirection.LOWER_IS_BETTER),
                    p50 = longRange(summaries.map { requireNotNull(it.latencyNs.p50) }, MetricDirection.LOWER_IS_BETTER),
                    p90 = longRange(summaries.map { requireNotNull(it.latencyNs.p90) }, MetricDirection.LOWER_IS_BETTER),
                    p95 = longRange(summaries.map { requireNotNull(it.latencyNs.p95) }, MetricDirection.LOWER_IS_BETTER),
                    p99 = longRange(summaries.map { requireNotNull(it.latencyNs.p99) }, MetricDirection.LOWER_IS_BETTER),
                    sum = longRange(summaries.map { it.latencyNs.sum }, MetricDirection.LOWER_IS_BETTER),
                ),
            throughput =
                AggregatedThroughputMetrics(
                    averageTps = doubleRange(summaries.map { requireNotNull(it.averageTps) }, MetricDirection.HIGHER_IS_BETTER),
                    elapsedNs = longRange(summaries.map { requireNotNull(it.elapsedNs) }, MetricDirection.LOWER_IS_BETTER),
                    peakTps = intRange(summaries.map { it.peakTps }, MetricDirection.HIGHER_IS_BETTER),
                ),
        )
    }

    private fun longRange(
        values: List<Long>,
        direction: MetricDirection,
    ): LongMetricRange {
        val minimum = values.min()
        val maximum = values.max()
        return LongMetricRange(
            direction = direction,
            unit = MetricUnit.NANOSECONDS,
            minimum = minimum,
            maximum = maximum,
            best = if (direction == MetricDirection.LOWER_IS_BETTER) minimum else maximum,
            worst = if (direction == MetricDirection.LOWER_IS_BETTER) maximum else minimum,
        )
    }

    private fun intRange(
        values: List<Int>,
        direction: MetricDirection,
    ): IntMetricRange {
        val minimum = values.min()
        val maximum = values.max()
        return IntMetricRange(
            direction = direction,
            unit = MetricUnit.TRANSACTIONS_PER_SECOND,
            minimum = minimum,
            maximum = maximum,
            best = if (direction == MetricDirection.LOWER_IS_BETTER) minimum else maximum,
            worst = if (direction == MetricDirection.LOWER_IS_BETTER) maximum else minimum,
        )
    }

    private fun doubleRange(
        values: List<Double>,
        direction: MetricDirection,
    ): DoubleMetricRange {
        val minimum = values.min()
        val maximum = values.max()
        return DoubleMetricRange(
            direction = direction,
            unit =
                if (direction == MetricDirection.LOWER_IS_BETTER) {
                    MetricUnit.NANOSECONDS
                } else {
                    MetricUnit.TRANSACTIONS_PER_SECOND
                },
            minimum = minimum,
            maximum = maximum,
            best = if (direction == MetricDirection.LOWER_IS_BETTER) minimum else maximum,
            worst = if (direction == MetricDirection.LOWER_IS_BETTER) maximum else minimum,
        )
    }
}
