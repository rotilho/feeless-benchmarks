package dev.feeless.benchmarks.app

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FreshOutputDirectoryTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `creates a missing output directory`() {
        // Given
        val destination = temporaryDirectory.resolve("nested/result")

        // When
        val created = FreshOutputDirectory.create(destination)

        // Then
        assertTrue(Files.isDirectory(created))
        assertEquals(destination.toAbsolutePath(), created)
    }

    @Test
    fun `refuses to overwrite an existing path`() {
        // Given
        val destination = Files.createDirectory(temporaryDirectory.resolve("result"))

        // When
        val error = assertFailsWith<CliException> { FreshOutputDirectory.create(destination) }

        // Then
        assertTrue(error.message.orEmpty().startsWith("output path already exists:"))
    }

    @Test
    fun `replaces an existing suite output while preserving it as a backup`() {
        // Given
        val destination = Files.createDirectory(temporaryDirectory.resolve("full-suite"))
        Files.writeString(destination.resolve("samples.csv"), "previous")
        val replacements = mutableListOf<Pair<Path, Path>>()

        // When
        val created =
            FreshOutputDirectory.createReplacingWithBackup(destination) { existing, backup ->
                replacements += existing to backup
            }

        // Then
        val backup = temporaryDirectory.resolve("full-suite.previous")
        assertEquals(destination, created)
        assertTrue(Files.isDirectory(created))
        assertTrue(Files.notExists(created.resolve("samples.csv")))
        assertEquals("previous", Files.readString(backup.resolve("samples.csv")))
        assertEquals(destination to backup, replacements.single())
    }

    @Test
    fun `replacement mode creates a missing suite output without a warning`() {
        // Given
        val destination = temporaryDirectory.resolve("full-suite")
        val replacements = mutableListOf<Pair<Path, Path>>()

        // When
        val created =
            FreshOutputDirectory.createReplacingWithBackup(destination) { existing, backup ->
                replacements += existing to backup
            }

        // Then
        assertEquals(destination, created)
        assertTrue(Files.isDirectory(created))
        assertTrue(replacements.isEmpty())
    }

    @Test
    fun `preserves earlier suite backups`() {
        // Given
        val destination = Files.createDirectory(temporaryDirectory.resolve("full-suite"))
        Files.writeString(destination.resolve("run.txt"), "first replacement")
        Files.createDirectory(temporaryDirectory.resolve("full-suite.previous"))

        // When
        FreshOutputDirectory.createReplacingWithBackup(destination) { _, _ -> }

        // Then
        val backup = temporaryDirectory.resolve("full-suite.previous-2")
        assertEquals("first replacement", Files.readString(backup.resolve("run.txt")))
        assertTrue(Files.isDirectory(temporaryDirectory.resolve("full-suite.previous")))
    }

    @Test
    fun `removes an empty failed reservation but preserves partial evidence`() {
        val empty = Files.createDirectory(temporaryDirectory.resolve("empty"))
        val partial = Files.createDirectory(temporaryDirectory.resolve("partial"))
        Files.writeString(partial.resolve("samples.csv"), "partial")

        FreshOutputDirectory.removeIfEmpty(empty)
        FreshOutputDirectory.removeIfEmpty(partial)

        assertTrue(Files.notExists(empty))
        assertTrue(Files.isDirectory(partial))
    }
}
