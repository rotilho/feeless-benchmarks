package dev.feeless.benchmarks.atto

import dev.feeless.benchmarks.core.PublishAdapter
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.mysql.MySQLContainer

class AttoNodeEnvironment internal constructor(
    val baseUrl: String,
    val imageDigests: Map<String, String>,
    val runtimeConfiguration: Map<String, String>,
    private val network: Network,
    private val database: MySQLContainer,
    private val node: GenericContainer<Nothing>,
    private val publisher: AttoPublisher,
) : AutoCloseable {
    val adapter: PublishAdapter<AttoPublication> = publisher
    val storageVolumeName: String
        get() =
            database.containerInfo.mounts!!
                .first { it.destination?.path == MYSQL_DATA_PATH }
                .name!!

    fun nodeLogs(): String = node.logs

    override fun close() {
        var failure: Throwable? = null
        listOf<AutoCloseable>(publisher, node, database, network).forEach { resource ->
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
