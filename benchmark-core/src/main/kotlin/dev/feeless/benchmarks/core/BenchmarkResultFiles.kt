package dev.feeless.benchmarks.core

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

data class BenchmarkResultFiles(
    val samples: Path,
    val summary: Path,
    val manifest: Path?,
)

/** Writes a scenario's artifacts only when none of the requested targets exists. */
fun writeBenchmarkResult(
    outputDirectory: Path,
    stem: String,
    samples: Iterable<BenchmarkSample>,
    manifest: RunManifest? = null,
): BenchmarkResultFiles {
    requireValidOutputStem(stem)
    val materialized = samples.toList()
    val files =
        BenchmarkResultFiles(
            samples = outputDirectory.resolve("$stem-samples.csv"),
            summary = outputDirectory.resolve("$stem-summary.json"),
            manifest = manifest?.let { outputDirectory.resolve("$stem-manifest.json") },
        )
    val requestedPaths = listOfNotNull(files.samples, files.summary, files.manifest)
    requestedPaths.firstOrNull(Files::exists)?.let { existing ->
        throw FileAlreadyExistsException(existing.toString())
    }

    writeSamplesCsv(files.samples, materialized)
    writeSummaryJson(files.summary, materialized)
    if (manifest != null) {
        writeRunManifestJson(requireNotNull(files.manifest), manifest)
    }
    return files
}

private fun requireValidOutputStem(stem: String) {
    require(
        stem.isNotBlank() &&
            stem != "." &&
            stem != ".." &&
            '/' !in stem &&
            '\\' !in stem,
    ) { "output stem must be a non-blank file name without path separators" }
}
