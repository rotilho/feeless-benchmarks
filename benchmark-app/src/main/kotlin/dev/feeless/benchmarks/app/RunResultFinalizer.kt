package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import dev.feeless.benchmarks.core.BenchmarkStatistics
import dev.feeless.benchmarks.core.RunManifest
import dev.feeless.benchmarks.core.encodeSummaryJson
import dev.feeless.benchmarks.core.writeBenchmarkResult
import java.nio.file.Path

internal object RunResultFinalizer {
    fun write(
        outputDirectory: Path,
        stem: String,
        expectedCount: Int,
        samples: List<BenchmarkSample>,
        manifest: RunManifest,
        canonicalExpectedCount: Int?,
        report: (String) -> Unit,
    ) {
        val summary = BenchmarkStatistics.summarize(samples)
        if (canonicalExpectedCount != null) {
            RunAcceptance.requireClean(
                scenario = stem,
                expectedCount = expectedCount,
                summary = summary,
                canonicalExpectedCount = canonicalExpectedCount,
            )
        }

        val files = writeBenchmarkResult(outputDirectory, stem, samples, summary, manifest)
        report(encodeSummaryJson(summary).trimEnd())
        report("samples: ${files.samples}")
        report("summary: ${files.summary}")
        report("manifest: ${files.manifest}")

        if (canonicalExpectedCount == null) {
            RunAcceptance.requireClean(
                scenario = stem,
                expectedCount = expectedCount,
                summary = summary,
            )
        }
    }
}
