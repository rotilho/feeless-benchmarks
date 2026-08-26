package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import dev.feeless.benchmarks.core.readRunManifestJson
import dev.feeless.benchmarks.core.readSamplesCsv
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class RunServiceOutputTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `canonical standalone runs use current stems without changing fixture identity`() {
        // Given
        val cases =
            listOf(
                OutputCase(Implementation.NANO, "nano-serial", "nano-serial", "nano-serial"),
                OutputCase(Implementation.RSNANO, "nano-serial", "rsnano-serial", "rsnano-serial"),
                OutputCase(Implementation.ATTO, "atto-serial", "atto-serial", "atto-serial"),
            )

        cases.forEach { case ->
            val outputDirectory = Files.createDirectory(temporaryDirectory.resolve(case.outputStem))
            val stem = RunService.resultStem(case.implementation, case.fixture, case.defaultStem)

            // When
            RunResultFinalizer.write(
                outputDirectory = outputDirectory,
                stem = stem,
                expectedCount = 1,
                samples = listOf(successfulSample(case)),
                manifest = manifest(case.fixture),
                canonicalExpectedCount = null,
                report = {},
            )

            // Then
            assertEquals(case.outputStem, stem)
            assertEquals(
                setOf(
                    "${case.outputStem}-samples.csv",
                    "${case.outputStem}-summary.json",
                    "${case.outputStem}-manifest.json",
                ),
                Files.list(outputDirectory).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() },
            )
            assertEquals(
                case.fixture,
                readSamplesCsv(outputDirectory.resolve("${case.outputStem}-samples.csv")).single().fixture,
            )
            assertEquals(
                case.fixture,
                readRunManifestJson(outputDirectory.resolve("${case.outputStem}-manifest.json"))
                    .runtimeConfiguration["fixture"],
            )
        }
    }

    @Test
    fun `noncanonical standalone runs retain their existing default stems`() {
        // Given
        val cases =
            listOf(
                OutputCase(Implementation.NANO, "custom", "custom", "custom"),
                OutputCase(Implementation.RSNANO, "nano-custom", "rsnano-custom", "rsnano-custom"),
                OutputCase(Implementation.ATTO, "atto-custom", "atto-custom", "atto-custom"),
            )

        // When
        val stems = cases.map { case -> RunService.resultStem(case.implementation, case.fixture, case.defaultStem) }

        // Then
        assertEquals(cases.map(OutputCase::outputStem), stems)
    }

    private fun successfulSample(case: OutputCase) =
        BenchmarkSample(
            implementation = case.implementation.cliName,
            fixture = case.fixture,
            lane = "lane",
            sequence = 0,
            account = "account",
            hash = "hash",
            startMonotonicNs = 1,
            completionMonotonicNs = 2,
            latencyNs = 1,
            error = null,
        )

    private fun manifest(fixture: String) =
        RunManifestFactory.create(
            repositoryRoot = temporaryDirectory,
            fixturePaths = emptyList(),
            imageDigests = emptyMap(),
            runtimeConfiguration = mapOf("fixture" to fixture),
        )

    private data class OutputCase(
        val implementation: Implementation,
        val fixture: String,
        val defaultStem: String,
        val outputStem: String,
    )
}
