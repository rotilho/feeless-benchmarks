package dev.feeless.benchmarks.atto

import dev.feeless.benchmarks.core.PublishAdapter
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.mysql.MySQLContainer
import java.nio.file.Files
import java.nio.file.Path

class AttoNodeEnvironment internal constructor(
    val baseUrl: String,
    val imageDigests: Map<String, String>,
    val runtimeConfiguration: Map<String, String>,
    private val network: Network,
    private val database: MySQLContainer,
    private val node: GenericContainer<Nothing>,
    private val publisher: AttoPublisher,
    val storageDirectory: Path,
) : AutoCloseable {
    val adapter: PublishAdapter<AttoPublication> = publisher

    fun nodeLogs(): String = node.logs

    override fun close() {
        var failure: Throwable? = null
        val storageCleanup =
            AutoCloseable {
                check(storageDirectory.toFile().deleteRecursively() || Files.notExists(storageDirectory)) {
                    "failed to delete Atto MySQL data directory $storageDirectory"
                }
            }
        val storagePermissionCleanup = AutoCloseable { database.makeStorageHostDeletable() }
        listOf<AutoCloseable>(publisher, node, storagePermissionCleanup, database, network, storageCleanup).forEach { resource ->
            try {
                resource.close()
            } catch (error: Throwable) {
                if (failure == null) {
                    failure = error
                } else {
                    failure.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }
}
