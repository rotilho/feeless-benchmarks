package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import dev.feeless.benchmarks.core.readRunManifestJson
import dev.feeless.benchmarks.core.readSamplesCsv
import dev.feeless.benchmarks.core.readSummaryJson
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunResultFinalizerTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `standalone failure writes diagnostics before returning failure`() {
        // Given
        val output = Files.createDirectory(temporaryDirectory.resolve("standalone"))
        val failure = failedSample()

        // When
        assertFailsWith<IllegalStateException> {
            RunResultFinalizer.write(
                outputDirectory = output,
                stem = "fixture",
                expectedCount = 2,
                samples = listOf(failure),
                manifest = manifest(),
                canonicalExpectedCount = null,
                report = {},
            )
        }

        // Then
        assertEquals(listOf(failure), readSamplesCsv(output.resolve("fixture-samples.csv")))
        assertEquals(1, readSummaryJson(output.resolve("fixture-summary.json")).errorCount)
        assertEquals("unknown", readRunManifestJson(output.resolve("fixture-manifest.json")).runnerRevision)
    }

    @Test
    fun `suite failure is rejected before writing artifacts`() {
        // Given
        val output = Files.createDirectory(temporaryDirectory.resolve("suite"))

        // When
        assertFailsWith<IllegalStateException> {
            RunResultFinalizer.write(
                outputDirectory = output,
                stem = "fixture",
                expectedCount = 1_000,
                samples = listOf(failedSample()),
                manifest = manifest(),
                canonicalExpectedCount = 1_000,
                report = {},
            )
        }

        // Then
        Files.list(output).use { files -> assertTrue(files.findAny().isEmpty) }
    }

    private fun manifest() =
        RunManifestFactory.create(
            repositoryRoot = temporaryDirectory,
            fixturePaths = emptyList(),
            imageDigests = emptyMap(),
            runtimeConfiguration = emptyMap(),
        )

    private fun failedSample() =
        BenchmarkSample(
            implementation = "test",
            fixture = "fixture",
            lane = "lane",
            sequence = 0,
            account = "account",
            hash = "hash",
            startMonotonicNs = 1,
            completionMonotonicNs = null,
            latencyNs = null,
            error = "rejected",
        )
}
