package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.AggregatedBenchmarkRun
import dev.feeless.benchmarks.core.BenchmarkAggregationInput
import dev.feeless.benchmarks.core.BenchmarkSummaryAggregator
import dev.feeless.benchmarks.core.readRunManifestJson
import dev.feeless.benchmarks.core.readSummaryJson
import dev.feeless.benchmarks.core.writeBenchmarkAggregateJson
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class AggregateResultsService(
    private val report: (String) -> Unit = ::println,
) {
    fun aggregate(command: Command.AggregateResults) {
        val inputRoot = command.inputRoot.toAbsolutePath().normalize()
        require(Files.isDirectory(inputRoot)) { "aggregate input root does not exist: $inputRoot" }

        val inputs =
            command.implementations.map { implementation ->
                loadImplementation(inputRoot, implementation, command.accountCount)
            }
        val aggregate =
            BenchmarkSummaryAggregator.aggregate(
                inputs = inputs,
                expectedRuns = command.expectedRuns,
                expectedSampleCount =
                    CanonicalScenarios
                        .independentAccounts(command.implementations.first(), command.accountCount)
                        .expectedCount,
            )
        val markdown = BenchmarkAggregateMarkdown.render(aggregate, command.accountCount)

        val outputDirectory = FreshOutputDirectory.create(command.outputDirectory)
        try {
            val jsonPath = outputDirectory.resolve(jsonFileName(command.accountCount))
            val markdownPath = outputDirectory.resolve(markdownFileName(command.accountCount))
            writeBenchmarkAggregateJson(jsonPath, aggregate)
            Files.writeString(
                markdownPath,
                markdown,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
            )
            report("aggregate JSON: $jsonPath")
            report("aggregate Markdown: $markdownPath")
        } catch (error: Throwable) {
            FreshOutputDirectory.removeIfEmpty(outputDirectory)
            throw error
        }
    }

    private fun loadImplementation(
        inputRoot: Path,
        implementation: Implementation,
        accountCount: Int,
    ): BenchmarkAggregationInput {
        val scenario = CanonicalScenarios.independentAccounts(implementation, accountCount)
        val summaryFileName = "${scenario.outputName}-summary.json"
        val summaryPaths =
            Files.walk(inputRoot).use { paths ->
                paths
                    .filter { path -> Files.isRegularFile(path) && path.fileName.toString() == summaryFileName }
                    .sorted()
                    .toList()
            }
        val runs = summaryPaths.map { summaryPath -> loadRun(inputRoot, scenario, summaryPath) }
        return BenchmarkAggregationInput(
            implementation = implementation.cliName,
            scenario = scenario.outputName,
            fixture = scenario.fixture,
            runs = runs,
        )
    }

    private fun loadRun(
        inputRoot: Path,
        scenario: SuiteScenario,
        summaryPath: Path,
    ): AggregatedBenchmarkRun {
        val manifestPath = summaryPath.resolveSibling("${scenario.outputName}-manifest.json")
        require(Files.isRegularFile(manifestPath)) {
            "${relativeSource(inputRoot, summaryPath)}: sibling manifest is missing"
        }
        return try {
            AggregatedBenchmarkRun(
                source = relativeSource(inputRoot, summaryPath),
                summary = readSummaryJson(summaryPath),
                manifest = readRunManifestJson(manifestPath),
            )
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "cannot read ${relativeSource(inputRoot, summaryPath)}: ${error.message}",
                error,
            )
        }
    }

    private fun relativeSource(
        inputRoot: Path,
        path: Path,
    ): String = inputRoot.relativize(path.toAbsolutePath().normalize()).joinToString("/")

    private companion object {
        fun jsonFileName(accountCount: Int) = "$accountCount-account-ranges.json"

        fun markdownFileName(accountCount: Int) = "$accountCount-account-ranges.md"
    }
}
