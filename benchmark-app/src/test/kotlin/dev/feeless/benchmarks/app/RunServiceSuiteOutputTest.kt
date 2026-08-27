package dev.feeless.benchmarks.app

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RunServiceSuiteOutputTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `suite moves an existing output aside and warns before running`() =
        runTest {
            // Given
            val repositoryRoot = Files.createDirectory(temporaryDirectory.resolve("repository"))
            val fixturesDirectory = Files.createDirectory(repositoryRoot.resolve("fixtures"))
            val outputRoot = Files.createDirectory(temporaryDirectory.resolve("full-suite"))
            Files.writeString(outputRoot.resolve("previous.txt"), "previous result")
            val warnings = mutableListOf<String>()
            val service = RunService(repositoryRoot, report = {}, warning = warnings::add)
            val command =
                Command.RunSuite(
                    implementations = listOf(Implementation.ATTO),
                    fixturesDirectory = fixturesDirectory,
                    outputRoot = outputRoot,
                    timeout = 1.seconds,
                )

            // When
            assertFailsWith<IllegalArgumentException> { service.runSuite(command) }

            // Then
            val backup = temporaryDirectory.resolve("full-suite.previous")
            assertEquals("previous result", Files.readString(backup.resolve("previous.txt")))
            assertEquals(
                "warning: moved existing output root $outputRoot to $backup",
                warnings.single(),
            )
        }

    @Test
    fun `suite refuses an output root containing the repository`() =
        runTest {
            // Given
            val repositoryRoot = Files.createDirectory(temporaryDirectory.resolve("repository"))
            val service = RunService(repositoryRoot, report = {}, warning = {})
            val command =
                Command.RunSuite(
                    implementations = listOf(Implementation.ATTO),
                    fixturesDirectory = repositoryRoot.resolve("fixtures"),
                    outputRoot = temporaryDirectory,
                    timeout = 1.seconds,
                )

            // When
            val error = assertFailsWith<CliException> { service.runSuite(command) }

            // Then
            assertEquals(
                "suite output root cannot contain the repository: $temporaryDirectory",
                error.message,
            )
            assertTrue(Files.isDirectory(repositoryRoot))
        }
}
