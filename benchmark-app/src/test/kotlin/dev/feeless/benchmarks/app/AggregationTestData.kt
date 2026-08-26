package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.AggregatedBenchmarkRun
import dev.feeless.benchmarks.core.BenchmarkAggregationInput
import dev.feeless.benchmarks.core.BenchmarkSummary
import dev.feeless.benchmarks.core.CpuDetails
import dev.feeless.benchmarks.core.JavaRuntimeDetails
import dev.feeless.benchmarks.core.LatencySummary
import dev.feeless.benchmarks.core.OperatingSystemDetails
import dev.feeless.benchmarks.core.RunManifest
import dev.feeless.benchmarks.core.writeRunManifestJson
import dev.feeless.benchmarks.core.writeSummaryJson
import java.nio.file.Files
import java.nio.file.Path

internal fun aggregationInput(
    implementation: Implementation,
    vararg runs: AggregatedBenchmarkRun,
): BenchmarkAggregationInput {
    val scenario = CanonicalScenarios.independentAccounts(implementation, DEFAULT_ACCOUNT_COUNT)
    return BenchmarkAggregationInput(
        implementation = implementation.cliName,
        scenario = scenario.outputName,
        fixture = scenario.fixture,
        runs = runs.toList(),
    )
}

internal fun aggregationRun(
    implementation: Implementation,
    source: String,
    latencyNs: Long,
    averageTps: Double,
    peakTps: Int,
    runnerRevision: String = "abc123",
    imageDigest: String = "sha256:image",
    accountCount: Int = DEFAULT_ACCOUNT_COUNT,
): AggregatedBenchmarkRun =
    AggregatedBenchmarkRun(
        source = source,
        summary = aggregationSummary(latencyNs, averageTps, peakTps, accountCount),
        manifest = aggregationManifest(implementation, runnerRevision, imageDigest, accountCount),
    )

internal fun writeAggregationRun(
    inputRoot: Path,
    runName: String,
    implementation: Implementation,
    latencyNs: Long,
    averageTps: Double,
    peakTps: Int,
    runnerRevision: String = "abc123",
    imageDigest: String = "sha256:image",
    accountCount: Int = DEFAULT_ACCOUNT_COUNT,
) {
    val scenario = CanonicalScenarios.independentAccounts(implementation, accountCount)
    val directory = Files.createDirectories(inputRoot.resolve(runName).resolve(scenario.outputName))
    writeSummaryJson(
        directory.resolve("${scenario.outputName}-summary.json"),
        aggregationSummary(latencyNs, averageTps, peakTps, accountCount),
    )
    writeRunManifestJson(
        directory.resolve("${scenario.outputName}-manifest.json"),
        aggregationManifest(implementation, runnerRevision, imageDigest, accountCount),
    )
}

private fun aggregationSummary(
    latencyNs: Long,
    averageTps: Double,
    peakTps: Int,
    accountCount: Int,
): BenchmarkSummary {
    val sampleCount = accountCount * ITEMS_PER_ACCOUNT
    return BenchmarkSummary(
        averageTps = averageTps,
        elapsedNs = (sampleCount * 1_000_000_000L / averageTps).toLong(),
        errorCount = 0,
        latencyNs =
            LatencySummary(
                average = latencyNs.toDouble(),
                count = sampleCount,
                max = latencyNs + 4_000_000,
                min = latencyNs - 4_000_000,
                p50 = latencyNs,
                p90 = latencyNs + 1_000_000,
                p95 = latencyNs + 2_000_000,
                p99 = latencyNs + 3_000_000,
                sum = latencyNs * sampleCount,
            ),
        peakTps = peakTps,
        sampleCount = sampleCount,
        successCount = sampleCount,
    )
}

private fun aggregationManifest(
    implementation: Implementation,
    runnerRevision: String,
    imageDigest: String,
    accountCount: Int,
): RunManifest {
    val scenario = CanonicalScenarios.independentAccounts(implementation, accountCount)
    return RunManifest(
        runnerRevision = runnerRevision,
        fixtureHashes = mapOf("${scenario.fixture}.fixture" to "sha256:fixture"),
        imageDigests = mapOf("${implementation.cliName}:1" to imageDigest),
        java = JavaRuntimeDetails(vendor = "vendor", version = "21", vmName = "vm", vmVersion = "21"),
        operatingSystem = OperatingSystemDetails(architecture = "amd64", name = "Linux", version = "1"),
        cpu = CpuDetails(logicalProcessorCount = 8, model = "cpu"),
        storageProfile = "durable",
        runtimeConfiguration =
            mapOf(
                "implementation" to implementation.cliName,
                "fixture" to scenario.fixture,
                "timeout" to "1m",
            ),
    )
}

private const val DEFAULT_ACCOUNT_COUNT = 500
private const val ITEMS_PER_ACCOUNT = 100
