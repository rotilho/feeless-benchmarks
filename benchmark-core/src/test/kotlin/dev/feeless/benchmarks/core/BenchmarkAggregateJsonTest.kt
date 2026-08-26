package dev.feeless.benchmarks.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkAggregateJsonTest {
    @Test
    fun `aggregate JSON round trip preserves runs ranges and directions`() {
        // Given
        val aggregate = BenchmarkSummaryAggregator.aggregate(listOf(input()), expectedRuns = 1)

        // When
        val encoded = encodeBenchmarkAggregateJson(aggregate)
        val decoded = decodeBenchmarkAggregateJson(encoded)

        // Then
        assertEquals(aggregate, decoded)
        assertTrue(encoded.contains("\"lower_is_better\""))
        assertTrue(encoded.contains("\"higher_is_better\""))
        assertTrue(encoded.contains("\"source\": \"run-01/nano-100-summary.json\""))
        assertTrue(encoded.endsWith('\n'))
    }

    private fun input() =
        BenchmarkAggregationInput(
            implementation = "nano",
            scenario = "nano-100",
            fixture = "nano-100",
            runs =
                listOf(
                    AggregatedBenchmarkRun(
                        source = "run-01/nano-100-summary.json",
                        summary = summary(),
                        manifest = manifest(),
                    ),
                ),
        )

    private fun summary() =
        BenchmarkSummary(
            averageTps = 100.0,
            elapsedNs = 100_000_000_000,
            errorCount = 0,
            latencyNs =
                LatencySummary(
                    average = 200_000_000.0,
                    count = 10_000,
                    max = 204_000_000,
                    min = 196_000_000,
                    p50 = 200_000_000,
                    p90 = 201_000_000,
                    p95 = 202_000_000,
                    p99 = 203_000_000,
                    sum = 2_000_000_000_000,
                ),
            peakTps = 120,
            sampleCount = 10_000,
            successCount = 10_000,
        )

    private fun manifest() =
        RunManifest(
            runnerRevision = "abc123",
            fixtureHashes = linkedMapOf("z" to "last", "a" to "first"),
            imageDigests = linkedMapOf("z" to "last", "a" to "first"),
            java = JavaRuntimeDetails(vendor = "vendor", version = "21", vmName = "vm", vmVersion = "21"),
            operatingSystem = OperatingSystemDetails(architecture = "amd64", name = "Linux", version = "1"),
            cpu = CpuDetails(logicalProcessorCount = 8, model = "cpu"),
            storageProfile = "durable",
            runtimeConfiguration = linkedMapOf("implementation" to "nano", "fixture" to "nano-100"),
        )
}
