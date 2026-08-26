package dev.feeless.benchmarks.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BenchmarkOutputTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `result writer keeps the fixture identity independent from the output stem`() {
        // Given
        val samples = listOf(sample())
        val manifest = manifest()

        // When
        val files = writeBenchmarkResult(directory, "nano-serial", samples, manifest)

        // Then
        assertEquals(directory.resolve("nano-serial-samples.csv"), files.samples)
        assertEquals(directory.resolve("nano-serial-summary.json"), files.summary)
        assertEquals(directory.resolve("nano-serial-manifest.json"), files.manifest)
        val manifestPath = requireNotNull(files.manifest)
        assertEquals(samples, readSamplesCsv(files.samples))
        assertEquals(BenchmarkStatistics.summarize(samples), readSummaryJson(files.summary))
        assertEquals(manifest, readRunManifestJson(manifestPath))
        assertEquals("nano-genesis", readSamplesCsv(files.samples).single().fixture)
        assertEquals("nano-genesis", readRunManifestJson(manifestPath).runtimeConfiguration["fixture"])
    }

    @Test
    fun `result writer refuses all output when any requested target already exists`() {
        // Given
        val existingSummary = directory.resolve("nano-serial-summary.json")
        Files.writeString(existingSummary, "existing")

        // When
        val failure =
            assertFailsWith<FileAlreadyExistsException> {
                writeBenchmarkResult(directory, "nano-serial", listOf(sample()), manifest())
            }

        // Then
        assertEquals(existingSummary.toString(), failure.file)
        assertFalse(Files.exists(directory.resolve("nano-serial-samples.csv")))
        assertEquals("existing", Files.readString(existingSummary))
        assertFalse(Files.exists(directory.resolve("nano-serial-manifest.json")))
    }

    @Test
    fun `individual writers refuse overwriting existing artifacts`() {
        // Given
        val samplesPath = directory.resolve("samples.csv")
        val summaryPath = directory.resolve("summary.json")
        val manifestPath = directory.resolve("manifest.json")
        writeSamplesCsv(samplesPath, listOf(sample()))
        writeSummaryJson(summaryPath, listOf(sample()))
        writeRunManifestJson(manifestPath, manifest())

        // When
        val sampleFailure = assertFailsWith<FileAlreadyExistsException> { writeSamplesCsv(samplesPath, emptyList()) }
        val summaryFailure = assertFailsWith<FileAlreadyExistsException> { writeSummaryJson(summaryPath, emptyList()) }
        val manifestFailure = assertFailsWith<FileAlreadyExistsException> { writeRunManifestJson(manifestPath, manifest()) }

        // Then
        assertEquals(samplesPath.toString(), sampleFailure.file)
        assertEquals(summaryPath.toString(), summaryFailure.file)
        assertEquals(manifestPath.toString(), manifestFailure.file)
        assertEquals(listOf(sample()), readSamplesCsv(samplesPath))
    }

    @Test
    fun `result writer rejects stems that can escape the output directory`() {
        // Given
        val invalidStems = listOf("", " ", ".", "..", "../outside", "sub/path", "sub\\path")

        // When
        val failures =
            invalidStems.map { stem ->
                assertFailsWith<IllegalArgumentException> {
                    writeBenchmarkResult(directory, stem, listOf(sample()))
                }
            }

        // Then
        assertTrue(failures.all { failure -> failure.message == "output stem must be a non-blank file name without path separators" })
        assertEquals(emptyList(), Files.list(directory).use { paths -> paths.toList() })
    }

    private fun sample(): BenchmarkSample =
        BenchmarkSample(
            implementation = "nano",
            fixture = "nano-genesis",
            lane = "0",
            sequence = 0,
            account = "nano-account",
            hash = "ABC",
            startMonotonicNs = 10,
            completionMonotonicNs = 30,
            latencyNs = 20,
            error = null,
        )

    private fun manifest(): RunManifest =
        RunManifest(
            runnerRevision = "abc123",
            fixtureHashes = mapOf("fixture.json" to "sha256:fixture"),
            imageDigests = mapOf("nano" to "sha256:image"),
            java =
                JavaRuntimeDetails(
                    vendor = "vendor",
                    version = "21",
                    vmName = "vm",
                    vmVersion = "21.0.1",
                ),
            operatingSystem =
                OperatingSystemDetails(
                    architecture = "amd64",
                    name = "Linux",
                    version = "1",
                ),
            cpu = CpuDetails(logicalProcessorCount = 8, model = "test cpu"),
            storageProfile = "durable",
            runtimeConfiguration = mapOf("fixture" to "nano-genesis", "network" to "dev"),
        )
}
