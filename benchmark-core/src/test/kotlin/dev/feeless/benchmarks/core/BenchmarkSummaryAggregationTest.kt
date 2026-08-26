package dev.feeless.benchmarks.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BenchmarkSummaryAggregationTest {
    @Test
    fun `metric ranges choose best and worst according to direction`() {
        // Given
        val input =
            input(
                run("run-02/summary.json", latencyNs = 300_000_000, averageTps = 80.0, peakTps = 90),
                run("run-01/summary.json", latencyNs = 200_000_000, averageTps = 120.0, peakTps = 140),
            )

        // When
        val aggregate = BenchmarkSummaryAggregator.aggregate(listOf(input), expectedRuns = 2)

        // Then
        val implementation = aggregate.implementations.single()
        assertEquals(listOf("run-01/summary.json", "run-02/summary.json"), implementation.runs.map { it.source })
        assertEquals(MetricDirection.LOWER_IS_BETTER, implementation.metrics.latencyNs.p50.direction)
        assertEquals(200_000_000, implementation.metrics.latencyNs.p50.best)
        assertEquals(300_000_000, implementation.metrics.latencyNs.p50.worst)
        assertEquals(MetricDirection.HIGHER_IS_BETTER, implementation.metrics.throughput.averageTps.direction)
        assertEquals(120.0, implementation.metrics.throughput.averageTps.best)
        assertEquals(80.0, implementation.metrics.throughput.averageTps.worst)
        assertEquals(140, implementation.metrics.throughput.peakTps.best)
        assertEquals(90, implementation.metrics.throughput.peakTps.worst)
    }

    @Test
    fun `aggregation rejects a run that is not a clean ten thousand sample result`() {
        // Given
        val invalid =
            run("run-01/summary.json", latencyNs = 200_000_000, averageTps = 100.0, peakTps = 120).let { run ->
                run.copy(summary = run.summary.copy(successCount = 9_999, errorCount = 1))
            }

        // When
        val error =
            assertFailsWith<IllegalArgumentException> {
                BenchmarkSummaryAggregator.aggregate(listOf(input(invalid)), expectedRuns = 1)
            }

        // Then
        assertTrue(error.message.orEmpty().contains("success_count=9999"))
    }

    @Test
    fun `aggregation rejects inconsistent provenance within one implementation`() {
        // Given
        val first = run("run-01/summary.json", latencyNs = 200_000_000, averageTps = 100.0, peakTps = 120)
        val second =
            run("run-02/summary.json", latencyNs = 210_000_000, averageTps = 90.0, peakTps = 110).let { run ->
                run.copy(manifest = run.manifest.copy(imageDigests = mapOf("node:1" to "sha256:different")))
            }

        // When
        val error =
            assertFailsWith<IllegalArgumentException> {
                BenchmarkSummaryAggregator.aggregate(listOf(input(first, second)), expectedRuns = 2)
            }

        // Then
        assertTrue(error.message.orEmpty().contains("image digests differ"))
    }

    @Test
    fun `aggregation rejects dirty runner provenance`() {
        // Given
        val dirty =
            run("run-01/summary.json", latencyNs = 200_000_000, averageTps = 100.0, peakTps = 120).let { run ->
                run.copy(manifest = run.manifest.copy(runnerRevision = "abc-dirty-def"))
            }

        // When
        val error =
            assertFailsWith<IllegalArgumentException> {
                BenchmarkSummaryAggregator.aggregate(listOf(input(dirty)), expectedRuns = 1)
            }

        // Then
        assertTrue(error.message.orEmpty().contains("clean source tree"))
    }

    @Test
    fun `aggregation requires durable storage and one runner revision across implementations`() {
        // Given
        val nano = input(run("nano/summary.json", latencyNs = 200_000_000, averageTps = 100.0, peakTps = 120))
        val attoRun =
            run("atto/summary.json", latencyNs = 100_000_000, averageTps = 200.0, peakTps = 220).let { run ->
                run.copy(
                    manifest =
                        manifest("atto", "atto-100").copy(
                            runnerRevision = "different",
                        ),
                )
            }
        val atto = input("atto", "atto-100", attoRun)
        val nondurable =
            run("nano/summary.json", latencyNs = 200_000_000, averageTps = 100.0, peakTps = 120).let { run ->
                run.copy(manifest = run.manifest.copy(storageProfile = "temporary"))
            }

        // When
        val storageError =
            assertFailsWith<IllegalArgumentException> {
                BenchmarkSummaryAggregator.aggregate(listOf(input(nondurable)), expectedRuns = 1)
            }
        val revisionError =
            assertFailsWith<IllegalArgumentException> {
                BenchmarkSummaryAggregator.aggregate(listOf(nano, atto), expectedRuns = 1)
            }

        // Then
        assertTrue(storageError.message.orEmpty().contains("storage profile must be durable"))
        assertTrue(revisionError.message.orEmpty().contains("runner revision differs across implementations"))
    }

    private fun input(vararg runs: AggregatedBenchmarkRun) = input("nano", "nano-100", *runs)

    private fun input(
        implementation: String,
        fixture: String,
        vararg runs: AggregatedBenchmarkRun,
    ): BenchmarkAggregationInput =
        BenchmarkAggregationInput(
            implementation = implementation,
            scenario = "$implementation-100",
            fixture = fixture,
            runs = runs.toList(),
        )

    private fun run(
        source: String,
        latencyNs: Long,
        averageTps: Double,
        peakTps: Int,
    ): AggregatedBenchmarkRun =
        AggregatedBenchmarkRun(
            source = source,
            summary = summary(latencyNs, averageTps, peakTps),
            manifest = manifest(),
        )

    private fun summary(
        latencyNs: Long,
        averageTps: Double,
        peakTps: Int,
    ): BenchmarkSummary =
        BenchmarkSummary(
            averageTps = averageTps,
            elapsedNs = (10_000 * 1_000_000_000L / averageTps).toLong(),
            errorCount = 0,
            latencyNs =
                LatencySummary(
                    average = latencyNs.toDouble(),
                    count = 10_000,
                    max = latencyNs + 4_000_000,
                    min = latencyNs - 4_000_000,
                    p50 = latencyNs,
                    p90 = latencyNs + 1_000_000,
                    p95 = latencyNs + 2_000_000,
                    p99 = latencyNs + 3_000_000,
                    sum = latencyNs * 10_000,
                ),
            peakTps = peakTps,
            sampleCount = 10_000,
            successCount = 10_000,
        )

    private fun manifest(
        implementation: String = "nano",
        fixture: String = "nano-100",
    ): RunManifest =
        RunManifest(
            runnerRevision = "abc123",
            fixtureHashes = mapOf("$fixture.json" to "sha256:fixture"),
            imageDigests = mapOf("node:1" to "sha256:image"),
            java = JavaRuntimeDetails(vendor = "vendor", version = "21", vmName = "vm", vmVersion = "21"),
            operatingSystem = OperatingSystemDetails(architecture = "amd64", name = "Linux", version = "1"),
            cpu = CpuDetails(logicalProcessorCount = 8, model = "cpu"),
            storageProfile = "durable",
            runtimeConfiguration = mapOf("implementation" to implementation, "fixture" to fixture),
        )
}
