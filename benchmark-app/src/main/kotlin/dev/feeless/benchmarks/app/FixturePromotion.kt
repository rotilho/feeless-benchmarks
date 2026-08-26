package dev.feeless.benchmarks.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

internal object FixturePromotion {
    fun requireIdenticalAndPromote(
        firstGeneration: Path,
        secondGeneration: Path,
        destination: Path,
    ): List<Path> {
        val firstFiles = relativeFiles(firstGeneration)
        val secondFiles = relativeFiles(secondGeneration)
        require(firstFiles.isNotEmpty()) { "fixture generation produced no files" }
        require(firstFiles == secondFiles) {
            "fixture generations produced different file sets: first=$firstFiles second=$secondFiles"
        }

        firstFiles.forEach { relative ->
            val first = firstGeneration.resolve(relative)
            val second = secondGeneration.resolve(relative)
            require(Files.mismatch(first, second) == -1L) {
                "fixture generation is not deterministic: $relative"
            }
        }

        Files.createDirectories(destination)
        return firstFiles.map { relative ->
            val source = firstGeneration.resolve(relative)
            val target = destination.resolve(relative)
            target.parent?.let(Files::createDirectories)
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
        }
    }

    private fun relativeFiles(root: Path): List<Path> =
        Files.walk(root).use { paths ->
            paths
                .filter(Path::isRegularFile)
                .map { it.relativeTo(root) }
                .sorted()
                .toList()
        }
}
