package dev.feeless.benchmarks.app

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
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

    fun removeIfEmpty(path: Path) {
        runCatching { Files.deleteIfExists(path) }
    }
}
