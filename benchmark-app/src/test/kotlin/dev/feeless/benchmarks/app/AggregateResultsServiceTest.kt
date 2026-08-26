package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.readBenchmarkAggregateJson
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AggregateResultsServiceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `recursively loads every implementation and writes both aggregate formats`() {
        // Given
        val inputRoot = Files.createDirectory(temporaryDirectory.resolve("input"))
        val implementations = listOf(Implementation.NANO, Implementation.ATTO, Implementation.RSNANO)
        implementations.forEachIndexed { implementationIndex, implementation ->
            repeat(2) { runIndex ->
                writeAggregationRun(
                    inputRoot = inputRoot,
                    runName = "run-${runIndex + 1}",
                    implementation = implementation,
                    latencyNs = 200_000_000L + implementationIndex * 10_000_000L + runIndex * 1_000_000L,
                    averageTps = 100.0 + implementationIndex * 10.0 + runIndex,
                    peakTps = 120 + implementationIndex * 10 + runIndex,
                )
            }
        }
        val outputDirectory = temporaryDirectory.resolve("output")
        val messages = mutableListOf<String>()

        // When
        AggregateResultsService(messages::add).aggregate(
            Command.AggregateResults(inputRoot, outputDirectory, implementations, expectedRuns = 2, accountCount = 500),
        )

        // Then
        val jsonPath = outputDirectory.resolve("500-account-ranges.json")
        val markdownPath = outputDirectory.resolve("500-account-ranges.md")
        assertTrue(Files.isRegularFile(jsonPath))
        assertTrue(Files.isRegularFile(markdownPath))
        assertEquals(2, messages.size)
        val aggregate = readBenchmarkAggregateJson(jsonPath)
        assertEquals(listOf("nano", "atto", "rsnano"), aggregate.implementations.map { it.implementation })
        assertEquals(
            listOf(
                "run-1/nano-500/nano-500-summary.json",
                "run-2/nano-500/nano-500-summary.json",
            ),
            aggregate.implementations
                .first()
                .runs
                .map { it.source },
        )
    }

    @Test
    fun `rejects a missing run before reserving the output directory`() {
        // Given
        val inputRoot = Files.createDirectory(temporaryDirectory.resolve("input"))
        writeAggregationRun(inputRoot, "run-1", Implementation.NANO, 200_000_000, 100.0, 120)
        val outputDirectory = temporaryDirectory.resolve("output")

        // When
        val error =
            assertFailsWith<IllegalArgumentException> {
                AggregateResultsService {}.aggregate(
                    Command.AggregateResults(
                        inputRoot,
                        outputDirectory,
                        listOf(Implementation.NANO),
                        expectedRuns = 2,
                        accountCount = 500,
                    ),
                )
            }

        // Then
        assertTrue(error.message.orEmpty().contains("nano has 1 runs; expected 2"))
        assertFalse(Files.exists(outputDirectory))
    }

    @Test
    fun `rejects inconsistent manifest provenance before writing output`() {
        // Given
        val inputRoot = Files.createDirectory(temporaryDirectory.resolve("input"))
        writeAggregationRun(inputRoot, "run-1", Implementation.NANO, 200_000_000, 100.0, 120)
        writeAggregationRun(
            inputRoot,
            "run-2",
            Implementation.NANO,
            201_000_000,
            101.0,
            121,
            imageDigest = "sha256:different",
        )
        val outputDirectory = temporaryDirectory.resolve("output")

        // When
        val error =
            assertFailsWith<IllegalArgumentException> {
                AggregateResultsService {}.aggregate(
                    Command.AggregateResults(
                        inputRoot,
                        outputDirectory,
                        listOf(Implementation.NANO),
                        expectedRuns = 2,
                        accountCount = 500,
                    ),
                )
            }

        // Then
        assertTrue(error.message.orEmpty().contains("image digests differ"))
        assertFalse(Files.exists(outputDirectory))
    }

    @Test
    fun `refuses an existing aggregate output directory`() {
        // Given
        val inputRoot = Files.createDirectory(temporaryDirectory.resolve("input"))
        writeAggregationRun(inputRoot, "run-1", Implementation.NANO, 200_000_000, 100.0, 120)
        val outputDirectory = Files.createDirectory(temporaryDirectory.resolve("output"))

        // When
        val error =
            assertFailsWith<CliException> {
                AggregateResultsService {}.aggregate(
                    Command.AggregateResults(
                        inputRoot,
                        outputDirectory,
                        listOf(Implementation.NANO),
                        expectedRuns = 1,
                        accountCount = 500,
                    ),
                )
            }

        // Then
        assertTrue(error.message.orEmpty().startsWith("output path already exists:"))
    }

    @Test
    fun `can still aggregate previous one-hundred-account runs explicitly`() {
        // Given
        val inputRoot = Files.createDirectory(temporaryDirectory.resolve("previous-input"))
        writeAggregationRun(
            inputRoot,
            "run-1",
            Implementation.NANO,
            200_000_000,
            100.0,
            120,
            accountCount = 100,
        )
        val outputDirectory = temporaryDirectory.resolve("previous-output")

        // When
        AggregateResultsService {}.aggregate(
            Command.AggregateResults(
                inputRoot,
                outputDirectory,
                listOf(Implementation.NANO),
                expectedRuns = 1,
                accountCount = 100,
            ),
        )

        // Then
        assertTrue(Files.isRegularFile(outputDirectory.resolve("100-account-ranges.json")))
        assertTrue(Files.isRegularFile(outputDirectory.resolve("100-account-ranges.md")))
    }
}
