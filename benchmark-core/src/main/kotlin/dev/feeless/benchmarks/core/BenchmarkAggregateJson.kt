package dev.feeless.benchmarks.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private val aggregateJson =
    Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

fun encodeBenchmarkAggregateJson(aggregate: BenchmarkAggregate): String =
    aggregateJson.encodeToString(aggregate.withSortedManifestMappings()) + "\n"

fun decodeBenchmarkAggregateJson(json: String): BenchmarkAggregate = aggregateJson.decodeFromString(json)

fun writeBenchmarkAggregateJson(
    path: Path,
    aggregate: BenchmarkAggregate,
) {
    writeNewText(path, encodeBenchmarkAggregateJson(aggregate))
}

fun readBenchmarkAggregateJson(path: Path): BenchmarkAggregate =
    decodeBenchmarkAggregateJson(Files.readString(path, StandardCharsets.UTF_8))

private fun BenchmarkAggregate.withSortedManifestMappings(): BenchmarkAggregate =
    copy(
        implementations =
            implementations.map { implementation ->
                implementation.copy(
                    runs =
                        implementation.runs.map { run ->
                            run.copy(
                                manifest =
                                    run.manifest.copy(
                                        fixtureHashes = run.manifest.fixtureHashes.toSortedMap(),
                                        imageDigests = run.manifest.imageDigests.toSortedMap(),
                                        runtimeConfiguration = run.manifest.runtimeConfiguration.toSortedMap(),
                                    ),
                            )
                        },
                )
            },
    )
