package dev.feeless.benchmarks.app

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

internal object FreshOutputDirectory {
    fun create(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        absolute.parent?.let(Files::createDirectories)
        try {
            Files.createDirectory(absolute)
        } catch (_: FileAlreadyExistsException) {
            throw CliException("output path already exists: $absolute")
        }
        return absolute
    }

    fun createReplacingWithBackup(
        path: Path,
        onReplaced: (existing: Path, backup: Path) -> Unit,
    ): Path {
        val absolute = path.toAbsolutePath().normalize()
        if (!Files.exists(absolute, NOFOLLOW_LINKS)) return create(absolute)

        val backup = nextBackupPath(absolute)
        Files.move(absolute, backup)
        onReplaced(absolute, backup)
        return try {
            create(absolute)
        } catch (error: Throwable) {
            if (!Files.exists(absolute, NOFOLLOW_LINKS)) {
                runCatching { Files.move(backup, absolute) }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
    }

    fun removeIfEmpty(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }

    private fun nextBackupPath(path: Path): Path {
        val fileName = path.fileName?.toString() ?: throw CliException("cannot replace filesystem root: $path")
        var suffix = 1
        while (true) {
            val label = if (suffix == 1) "$fileName.previous" else "$fileName.previous-$suffix"
            val candidate = path.resolveSibling(label)
            if (!Files.exists(candidate, NOFOLLOW_LINKS)) return candidate
            suffix += 1
        }
    }
}
