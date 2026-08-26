package dev.feeless.benchmarks.nano

import java.nio.file.Files
import java.nio.file.Path

internal object NanoDataDirectoryCleanup {
    fun delete(
        directory: Path,
        repairPermissions: (Path) -> Unit,
    ) {
        if (directory.toFile().deleteRecursively() || Files.notExists(directory)) return

        repairPermissions(directory)
        check(directory.toFile().deleteRecursively() || Files.notExists(directory)) {
            "failed to delete Nano data directory $directory after repairing its permissions"
        }
    }
}
