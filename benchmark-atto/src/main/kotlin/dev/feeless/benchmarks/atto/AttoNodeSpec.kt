package dev.feeless.benchmarks.atto

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.toHex
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.PullPolicy
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import java.time.Duration as JavaDuration

class AttoNodeSpec(
    val pullImages: Boolean = true,
    val startupTimeout: Duration = 5.minutes,
    val storageRoot: Path = Path.of("build", "testcontainers-storage").toAbsolutePath(),
) {
    val nodeImage: String = ATTO_NODE_IMAGE
    val mysqlImage: String = ATTO_MYSQL_IMAGE

    fun start(initialFixture: Path): AttoNodeEnvironment {
        val genesis = AttoFixtures.readGenesis(initialFixture)
        require(genesis.block is AttoOpenBlock && genesis.block.network == AttoNetwork.LOCAL) {
            "Atto environment requires a LOCAL OPEN genesis transaction"
        }
        require(genesis.block.publicKey.toString() == GENESIS_PUBLIC_KEY) {
            "Atto genesis does not match the canonical voter private key"
        }

        Files.createDirectories(storageRoot)
        val fileStoreType = Files.getFileStore(storageRoot).type().lowercase()
        require("tmpfs" !in fileStoreType && "ramfs" !in fileStoreType) {
            "Atto durable storage root $storageRoot is backed by $fileStoreType"
        }
        val dataDirectory = Files.createTempDirectory(storageRoot, "feeless-atto-mysql-")
        runCatching { Files.setPosixFilePermissions(dataDirectory, PosixFilePermission.entries.toSet()) }

        val timeout = JavaDuration.ofMillis(startupTimeout.inWholeMilliseconds)
        var allocatedNetwork: Network? = null
        var allocatedDatabase: MySQLContainer? = null
        try {
            val network = Network.newNetwork().also { allocatedNetwork = it }
            val databasePassword = UUID.randomUUID().toString()
            val database =
                MySQLContainer(DockerImageName.parse(mysqlImage))
                    .withNetwork(network)
                    .withNetworkAliases(DATABASE_ALIAS)
                    .withDatabaseName(DATABASE_NAME)
                    .withUsername(DATABASE_USER)
                    .withPassword(databasePassword)
                    .withStartupTimeout(timeout)
                    .apply {
                        addFileSystemBind(
                            dataDirectory.toString(),
                            MYSQL_DATA_PATH,
                            BindMode.READ_WRITE,
                            SelinuxContext.SHARED,
                        )
                        if (pullImages) withImagePullPolicy(PullPolicy.alwaysPull())
                    }.also { allocatedDatabase = it }

            database.start()
            val node =
                GenericContainer<Nothing>(DockerImageName.parse(nodeImage)).apply {
                    withNetwork(network)
                    withNetworkAliases(NODE_ALIAS)
                    withExposedPorts(NODE_HTTP_PORT)
                    withStartupTimeout(timeout)
                    waitingFor(
                        Wait
                            .forHttp("/transactions/${genesis.hash}")
                            .forPort(NODE_HTTP_PORT)
                            .withStartupTimeout(timeout),
                    )
                    withEnv("SPRING_PROFILES_ACTIVE", "local")
                    withEnv("ATTO_DB_HOST", DATABASE_ALIAS)
                    withEnv("ATTO_DB_PORT", MYSQL_PORT.toString())
                    withEnv("ATTO_DB_NAME", DATABASE_NAME)
                    withEnv("ATTO_DB_USER", DATABASE_USER)
                    withEnv("ATTO_DB_PASSWORD", databasePassword)
                    withEnv("ATTO_PUBLIC_URI", "ws://$NODE_ALIAS:$NODE_NETWORK_PORT")
                    withEnv("ATTO_GENESIS", genesis.toHex())
                    withEnv("ATTO_NODE_FORCE_API", "true")
                    withEnv("ATTO_PRIVATE_KEY", ByteArray(32).toHex())
                    ATTO_LOGGING_ENVIRONMENT.forEach { (name, value) -> withEnv(name, value) }
                    if (pullImages) withImagePullPolicy(PullPolicy.alwaysPull())
                }

            try {
                node.start()
                val baseUrl = "http://${node.host}:${node.getMappedPort(NODE_HTTP_PORT)}"
                return AttoNodeEnvironment(
                    baseUrl = baseUrl,
                    imageDigests =
                        linkedMapOf(
                            nodeImage to node.resolvedImageDigest(),
                            mysqlImage to database.resolvedImageDigest(),
                        ),
                    runtimeConfiguration =
                        linkedMapOf(
                            "node.image" to nodeImage,
                            "node.profile" to "local",
                            "node.voters" to "1",
                            "database.image" to mysqlImage,
                            "database.name" to DATABASE_NAME,
                            "database.user" to DATABASE_USER,
                            "database.storage" to "durable",
                            "database.durability" to "mysql-defaults",
                            "database.password" to "<redacted>",
                            "client.connectionReuse" to "ktor-apache5-pool",
                            "client.maxConnectionsPerRoute" to MAX_CONCURRENT_ACCOUNTS.toString(),
                            "client.requestTimeout" to "disabled",
                            "node.forceApi" to "true",
                            "node.logging" to "cash.atto.node=INFO",
                            "pullImages" to pullImages.toString(),
                        ),
                    network = network,
                    database = database,
                    node = node,
                    publisher = AttoPublisher(baseUrl),
                    storageDirectory = dataDirectory,
                )
            } catch (error: Throwable) {
                runCatching { node.close() }
                throw error
            }
        } catch (error: Throwable) {
            allocatedDatabase?.let { database ->
                runCatching { database.makeStorageHostDeletable() }
                runCatching { database.close() }
            }
            allocatedNetwork?.let { network -> runCatching { network.close() } }
            runCatching { dataDirectory.toFile().deleteRecursively() }
            throw error
        }
    }
}

const val ATTO_NODE_IMAGE = "ghcr.io/attocash/node:1.34-live"
const val ATTO_MYSQL_IMAGE = "mysql:8.4"

private const val GENESIS_PUBLIC_KEY = "3B6A27BCCEB6A42D62A3A8D02A6F0D73653215771DE243A63AC048A18B59DA29"
private const val NODE_ALIAS = "atto-node"
private const val DATABASE_ALIAS = "atto-mysql"
private const val DATABASE_NAME = "atto"
private const val DATABASE_USER = "atto"
private const val NODE_HTTP_PORT = 8080
private const val NODE_NETWORK_PORT = 8082
private const val MYSQL_PORT = 3306
internal const val MYSQL_DATA_PATH = "/var/lib/mysql"

private val ATTO_LOGGING_ENVIRONMENT =
    mapOf(
        "LOGGING_LEVEL_CASH_ATTO_NODE" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_NETWORK" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_ELECTION" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_TRANSACTION" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_VOTE" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_BOOTSTRAP" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_TRANSACTION_VALIDATION" to "INFO",
        "LOGGING_LEVEL_CASH_ATTO_NODE_RECEIVABLE" to "INFO",
    )

internal fun MySQLContainer.makeStorageHostDeletable() {
    if (!isRunning) return

    val result = execInContainer("chmod", "-R", "a+rwx", MYSQL_DATA_PATH)
    check(result.exitCode == 0) {
        "failed to make Atto MySQL storage host-deletable: ${result.stderr.trim()}"
    }
}

private fun GenericContainer<*>.resolvedImageDigest(): String {
    val imageId = containerInfo.imageId
    return runCatching {
        dockerClient
            .inspectImageCmd(imageId)
            .exec()
            .repoDigests
            ?.firstOrNull()
            ?.substringAfterLast('@')
    }.getOrNull() ?: imageId
}
