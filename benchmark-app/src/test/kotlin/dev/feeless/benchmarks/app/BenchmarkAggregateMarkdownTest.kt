package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSummaryAggregator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BenchmarkAggregateMarkdownTest {
    @Test
    fun `renders whole millisecond latency ranges and decimal TPS ranges`() {
        // Given
        val input =
            aggregationInput(
                Implementation.NANO,
                aggregationRun(
                    Implementation.NANO,
                    "run-1/nano-500-summary.json",
                    latencyNs = 200_400_000,
                    averageTps = 100.124,
                    peakTps = 110,
                ),
                aggregationRun(
                    Implementation.NANO,
                    "run-2/nano-500-summary.json",
                    latencyNs = 300_400_000,
                    averageTps = 120.126,
                    peakTps = 130,
                ),
            )
        val aggregate = BenchmarkSummaryAggregator.aggregate(listOf(input), expectedRuns = 2, expectedSampleCount = 50_000)

        // When
        val markdown = BenchmarkAggregateMarkdown.render(aggregate, accountCount = 500)

        // Then
        assertTrue(markdown.contains("| Nano | 200-300 ms | 200-300 ms"))
        assertTrue(markdown.contains("| 100.12-120.13 | 110-130 |"))
        assertTrue(markdown.contains("Latency is rounded to whole milliseconds."))
        assertTrue(markdown.startsWith("# 500-account benchmark ranges"))
        assertFalse(Regex("""\d+\.\d+ ms""").containsMatchIn(markdown))
    }
}
