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
