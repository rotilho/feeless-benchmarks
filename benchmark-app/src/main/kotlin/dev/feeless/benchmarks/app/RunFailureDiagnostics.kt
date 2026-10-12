package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import java.nio.file.Files
import java.nio.file.Path

internal object RunFailureDiagnostics {
    fun capture(
        outputDirectory: Path,
        samples: List<BenchmarkSample>,
        nodeLogs: () -> String,
        warning: (String) -> Unit,
    ) {
        val failedSamples = samples.filter { sample -> sample.error != null }
        if (failedSamples.isEmpty()) return

        failedSamples.forEach { sample ->
            warning(
                "benchmark error: implementation=${sample.implementation}, fixture=${sample.fixture}, " +
                    "account=${sample.account}, lane=${sample.lane}, sequence=${sample.sequence}, " +
                    "hash=${sample.hash}\n${sample.error}",
            )
        }

        val nodeLogPath = outputDirectory.resolve("node.log")
        try {
            Files.writeString(nodeLogPath, nodeLogs())
        } catch (error: Exception) {
            val detail = error.message ?: error::class.java.simpleName
            warning("warning: could not save node logs to $nodeLogPath: $detail")
            return
        }

        warning("node logs: $nodeLogPath")
    }
}
