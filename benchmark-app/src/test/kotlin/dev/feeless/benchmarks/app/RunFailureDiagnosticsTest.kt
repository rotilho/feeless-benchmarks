package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunFailureDiagnosticsTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `failed samples report context and preserve full node logs`() {
        // Given
        val outputDirectory = Files.createDirectory(temporaryDirectory.resolve("failed"))
        val sample = failedSample()
        val secondSample = sample.copy(account = "account-2", sequence = 4, hash = "hash-5", error = "second error")
        val warnings = mutableListOf<String>()
        val nodeLog = "node startup\nnode error details\n"
        var nodeLogCalls = 0

        // When
        RunFailureDiagnostics.capture(
            outputDirectory = outputDirectory,
            samples = listOf(sample, secondSample),
            nodeLogs = {
                nodeLogCalls++
                nodeLog
            },
            warning = { message -> warnings.add(message) },
        )

        // Then
        val errorReport = warnings.first()
        assertTrue(errorReport.contains("implementation=atto"))
        assertTrue(errorReport.contains("fixture=fixture"))
        assertTrue(errorReport.contains("account=account-1"))
        assertTrue(errorReport.contains("lane=lane-2"))
        assertTrue(errorReport.contains("sequence=3"))
        assertTrue(errorReport.contains("hash=hash-4"))
        assertTrue(errorReport.endsWith(sample.error!!))
        assertEquals(3, warnings.size)
        assertTrue(warnings[1].contains("account=account-2"))
        assertTrue(warnings[1].contains("hash=hash-5"))
        assertTrue(warnings[1].endsWith("second error"))
        assertEquals(1, nodeLogCalls)
        val nodeLogPath = outputDirectory.resolve("node.log")
        assertEquals(nodeLog, Files.readString(nodeLogPath))
        assertEquals("node logs: $nodeLogPath", warnings[2])
    }

    @Test
    fun `successful samples do not log or create diagnostics`() {
        // Given
        val outputDirectory = Files.createDirectory(temporaryDirectory.resolve("successful"))
        val warnings = mutableListOf<String>()
        var nodeLogCalls = 0
        val sample = failedSample().copy(error = null)

        // When
        RunFailureDiagnostics.capture(
            outputDirectory = outputDirectory,
            samples = listOf(sample),
            nodeLogs = {
                nodeLogCalls++
                "unexpected logs"
            },
            warning = { message -> warnings.add(message) },
        )

        // Then
        assertTrue(warnings.isEmpty())
        assertEquals(0, nodeLogCalls)
        assertTrue(Files.list(outputDirectory).use { files -> files.findAny().isEmpty })
    }

    @Test
    fun `node log collection failure keeps sample error visible`() {
        // Given
        val outputDirectory = Files.createDirectory(temporaryDirectory.resolve("collection-failure"))
        val sample = failedSample()
        val warnings = mutableListOf<String>()

        // When
        RunFailureDiagnostics.capture(
            outputDirectory = outputDirectory,
            samples = listOf(sample),
            nodeLogs = { throw IOException("node log collection failed") },
            warning = { message -> warnings.add(message) },
        )

        // Then
        assertTrue(warnings.first().contains(sample.error!!))
        assertTrue(warnings.last().contains("could not save node logs"))
        assertTrue(warnings.last().contains("node log collection failed"))
        assertTrue(Files.list(outputDirectory).use { files -> files.findAny().isEmpty })
    }

    @Test
    fun `node log write failure keeps sample error visible`() {
        // Given
        val outputFile = Files.createFile(temporaryDirectory.resolve("not-a-directory"))
        val sample = failedSample()
        val warnings = mutableListOf<String>()

        // When
        RunFailureDiagnostics.capture(
            outputDirectory = outputFile,
            samples = listOf(sample),
            nodeLogs = { "node logs" },
            warning = { message -> warnings.add(message) },
        )

        // Then
        assertTrue(warnings.first().contains(sample.error!!))
        assertTrue(warnings.last().contains("could not save node logs"))
    }

    private fun failedSample() =
        BenchmarkSample(
            implementation = "atto",
            fixture = "fixture",
            lane = "lane-2",
            sequence = 3,
            account = "account-1",
            hash = "hash-4",
            startMonotonicNs = 1,
            completionMonotonicNs = null,
            latencyNs = null,
            error = "transaction rejected\nHTTP 422: INVALID_TIMESTAMP",
        )
}
