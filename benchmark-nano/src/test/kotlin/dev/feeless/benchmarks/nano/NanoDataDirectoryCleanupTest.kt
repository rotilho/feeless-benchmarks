package dev.feeless.benchmarks.nano

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NanoDataDirectoryCleanupTest {
    @Test
    fun `permission repair is skipped when the data directory is directly deletable`() {
        // Given
        val directory = Files.createTempDirectory("nano-cleanup-accessible-")
        Files.writeString(directory.resolve("ledger"), "data")
        var repairs = 0

        // When
        NanoDataDirectoryCleanup.delete(directory) { repairs++ }

        // Then
        assertFalse(Files.exists(directory))
        assertEquals(0, repairs)
    }

    @Test
    fun `permission repair is used when a container-owned subtree is inaccessible`() {
        // Given
        val directory = Files.createTempDirectory("nano-cleanup-inaccessible-")
        val locked = Files.createDirectory(directory.resolve("backup"))
        Files.writeString(locked.resolve("ledger"), "data")
        Files.setPosixFilePermissions(locked, emptySet())
        var repairs = 0

        try {
            // When
            NanoDataDirectoryCleanup.delete(directory) { path ->
                repairs++
                restoreOwnerPermissions(path.resolve("backup"))
            }

            // Then
            assertFalse(Files.exists(directory))
            assertEquals(1, repairs)
        } finally {
            if (Files.exists(locked)) restoreOwnerPermissions(locked)
            directory.toFile().deleteRecursively()
        }
    }

    private fun restoreOwnerPermissions(path: Path) {
        Files.setPosixFilePermissions(
            path,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )
    }
}
