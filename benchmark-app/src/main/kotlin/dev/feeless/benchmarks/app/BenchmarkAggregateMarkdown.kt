package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkAggregate
import dev.feeless.benchmarks.core.DoubleMetricRange
import dev.feeless.benchmarks.core.IntMetricRange
import dev.feeless.benchmarks.core.LongMetricRange
import java.util.Locale
import kotlin.math.roundToLong

internal object BenchmarkAggregateMarkdown {
    fun render(
        aggregate: BenchmarkAggregate,
        accountCount: Int,
    ): String =
        buildString {
            val runLabel = if (aggregate.expectedRuns == 1) "run" else "runs"
            appendLine("# $accountCount-account benchmark ranges")
            appendLine()
            appendLine(
                "Ranges across ${aggregate.expectedRuns} accepted $runLabel per implementation. " +
                    "Latency is rounded to whole milliseconds.",
            )
            appendLine()
            appendLine(
                "| Implementation | Average latency ↓ | P50 ↓ | P90 ↓ | P95 ↓ | P99 ↓ | Average TPS ↑ | Peak TPS ↑ |",
            )
            appendLine("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
            aggregate.implementations.forEach { implementation ->
                val latency = implementation.metrics.latencyNs
                val throughput = implementation.metrics.throughput
                appendLine(
                    "| ${implementation.implementation.displayName()} " +
                        "| ${latency.average.wholeMilliseconds()} " +
                        "| ${latency.p50.wholeMilliseconds()} " +
                        "| ${latency.p90.wholeMilliseconds()} " +
                        "| ${latency.p95.wholeMilliseconds()} " +
                        "| ${latency.p99.wholeMilliseconds()} " +
                        "| ${throughput.averageTps.decimalRange()} " +
                        "| ${throughput.peakTps.integerRange()} |",
                )
            }
        }

    private fun String.displayName(): String =
        when (this) {
            "nano" -> "Nano"
            "atto" -> "Atto"
            "rsnano" -> "RSNano"
            else -> replaceFirstChar(Char::uppercase)
        }

    private fun DoubleMetricRange.wholeMilliseconds(): String =
        displayRange(
            (minimum / NANOSECONDS_PER_MILLISECOND).roundToLong(),
            (maximum / NANOSECONDS_PER_MILLISECOND).roundToLong(),
            "ms",
        )

    private fun LongMetricRange.wholeMilliseconds(): String =
        displayRange(
            (minimum.toDouble() / NANOSECONDS_PER_MILLISECOND).roundToLong(),
            (maximum.toDouble() / NANOSECONDS_PER_MILLISECOND).roundToLong(),
            "ms",
        )

    private fun DoubleMetricRange.decimalRange(): String {
        val minimumText = String.format(Locale.ROOT, "%.2f", minimum)
        val maximumText = String.format(Locale.ROOT, "%.2f", maximum)
        return if (minimumText == maximumText) minimumText else "$minimumText-$maximumText"
    }

    private fun IntMetricRange.integerRange(): String = if (minimum == maximum) minimum.toString() else "$minimum-$maximum"

    private fun displayRange(
        minimum: Long,
        maximum: Long,
        unit: String,
    ): String = if (minimum == maximum) "$minimum $unit" else "$minimum-$maximum $unit"

    private const val NANOSECONDS_PER_MILLISECOND = 1_000_000.0
}
