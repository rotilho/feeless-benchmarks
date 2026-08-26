package dev.feeless.benchmarks.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private val benchmarkJson =
    Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

fun encodeSummaryJson(summary: BenchmarkSummary): String = benchmarkJson.encodeToString(summary) + "\n"

fun decodeSummaryJson(json: String): BenchmarkSummary = benchmarkJson.decodeFromString(json)

fun writeSummaryJson(
    path: Path,
    summary: BenchmarkSummary,
) {
    writeNewText(path, encodeSummaryJson(summary))
}

fun writeSummaryJson(
    path: Path,
    samples: Iterable<BenchmarkSample>,
): BenchmarkSummary {
    val summary = BenchmarkStatistics.summarize(samples)
    writeSummaryJson(path, summary)
    return summary
}

fun readSummaryJson(path: Path): BenchmarkSummary = decodeSummaryJson(Files.readString(path, StandardCharsets.UTF_8))
