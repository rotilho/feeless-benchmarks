package dev.feeless.benchmarks.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BenchmarkStatisticsTest {
    @Test
    fun `nearest rank uses the ceiling rank`() {
        // Given
        val values = listOf(40L, 10L, 30L, 20L)

        // When
        val percentiles =
            listOf(
                BenchmarkStatistics.nearestRank(values, 0.0),
                BenchmarkStatistics.nearestRank(values, 25.0),
                BenchmarkStatistics.nearestRank(values, 50.0),
                BenchmarkStatistics.nearestRank(values, 99.0),
                BenchmarkStatistics.nearestRank(values, 100.0),
            )

        // Then
        assertEquals(listOf(10L, 10L, 20L, 40L, 40L), percentiles)
    }

    @Test
    fun `nearest rank rejects empty values and invalid percentiles`() {
        // Given
        val values = listOf(1L)

        // When
        val empty = assertFailsWith<IllegalArgumentException> { BenchmarkStatistics.nearestRank(emptyList(), 50.0) }
        val negative = assertFailsWith<IllegalArgumentException> { BenchmarkStatistics.nearestRank(values, -1.0) }
        val excessive = assertFailsWith<IllegalArgumentException> { BenchmarkStatistics.nearestRank(values, 101.0) }
        val notANumber = assertFailsWith<IllegalArgumentException> { BenchmarkStatistics.nearestRank(values, Double.NaN) }

        // Then
        assertEquals("values must not be empty", empty.message)
        assertEquals("percentile must be between 0 and 100", negative.message)
        assertEquals("percentile must be between 0 and 100", excessive.message)
        assertEquals("percentile must be between 0 and 100", notANumber.message)
    }

    @Test
    fun `latency summary uses nearest rank percentiles`() {
        // Given
        val values = listOf(40L, 10L, 30L, 20L)

        // When
        val summary = BenchmarkStatistics.summarizeLatencies(values)

        // Then
        assertEquals(
            LatencySummary(
                average = 25.0,
                count = 4,
                max = 40,
                min = 10,
                p50 = 20,
                p90 = 40,
                p95 = 40,
                p99 = 40,
                sum = 100,
            ),
            summary,
        )
    }

    @Test
    fun `empty latency summary has no undefined values`() {
        // Given
        val values = emptyList<Long>()

        // When
        val summary = BenchmarkStatistics.summarizeLatencies(values)

        // Then
        assertEquals(
            LatencySummary(
                average = null,
                count = 0,
                max = null,
                min = null,
                p50 = null,
                p90 = null,
                p95 = null,
                p99 = null,
                sum = 0,
            ),
            summary,
        )
    }

    @Test
    fun `peak throughput uses half open one second windows`() {
        // Given
        val completions = listOf(1_000_000_000L, 0L, 999_999_999L)

        // When
        val peak = BenchmarkStatistics.peakOneSecondCompletions(completions)
        val emptyPeak = BenchmarkStatistics.peakOneSecondCompletions(emptyList())

        // Then
        assertEquals(2, peak)
        assertEquals(0, emptyPeak)
    }

    @Test
    fun `throughput uses run elapsed time and completion windows`() {
        // Given
        val starts = listOf(0L, 500_000_000L, 1_000_000_000L)
        val completions = listOf(500_000_000L, 1_000_000_000L, 2_000_000_000L)

        // When
        val summary = BenchmarkStatistics.summarizeThroughput(starts, completions)

        // Then
        assertEquals(
            ThroughputSummary(elapsedNs = 2_000_000_000, averageTps = 1.5, peakTps = 2),
            summary,
        )
    }

    @Test
    fun `throughput handles empty and nonpositive elapsed time`() {
        // Given
        val empty = emptyList<Long>()

        // When
        val emptySummary = BenchmarkStatistics.summarizeThroughput(empty, empty)
        val zeroSummary = BenchmarkStatistics.summarizeThroughput(listOf(10L), listOf(10L))
        val negativeSummary = BenchmarkStatistics.summarizeThroughput(listOf(11L), listOf(10L))

        // Then
        assertEquals(ThroughputSummary(null, null, 0), emptySummary)
        assertEquals(ThroughputSummary(0, null, 1), zeroSummary)
        assertEquals(ThroughputSummary(-1, null, 1), negativeSummary)
    }

    @Test
    fun `throughput rejects unpaired timestamps`() {
        // Given
        val starts = listOf(1L)
        val completions = emptyList<Long>()

        // When
        val failure =
            assertFailsWith<IllegalArgumentException> {
                BenchmarkStatistics.summarizeThroughput(starts, completions)
            }

        // Then
        assertEquals("start and completion timestamps must have the same count", failure.message)
    }

    @Test
    fun `benchmark summary derives values only from complete successes`() {
        // Given
        val samples =
            listOf(
                sample(start = 0, completion = 1, latency = 1, error = null),
                sample(start = 100_000_000, completion = 500_000_000, latency = 400_000_000, error = null),
                sample(start = 200_000_000, completion = 300_000_000, latency = 100_000_000, error = "rejected"),
                sample(start = 300_000_000, completion = null, latency = null, error = null),
            )

        // When
        val summary = BenchmarkStatistics.summarize(samples)

        // Then
        assertEquals(4, summary.sampleCount)
        assertEquals(2, summary.successCount)
        assertEquals(1, summary.errorCount)
        assertEquals(200_000_000.5, summary.latencyNs.average)
        assertEquals(500_000_000, summary.elapsedNs)
        assertEquals(4.0, summary.averageTps)
        assertEquals(2, summary.peakTps)
    }

    private fun sample(
        start: Long,
        completion: Long?,
        latency: Long?,
        error: String?,
    ): BenchmarkSample =
        BenchmarkSample(
            implementation = "node",
            fixture = "fixture",
            lane = "a",
            sequence = 1,
            account = "account",
            hash = "hash",
            startMonotonicNs = start,
            completionMonotonicNs = completion,
            latencyNs = latency,
            error = error,
        )
}
