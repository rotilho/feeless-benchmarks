package dev.feeless.benchmarks.nano

import dev.feeless.benchmarks.core.VIRTUAL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.SelinuxContext
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

data class NanoNodeSpec(
    val implementation: String,
    val startupTimeout: Duration = 60.seconds,
    val storageRoot: Path = Path.of("build", "testcontainers-storage").toAbsolutePath(),
) {
    init {
        require(implementation == "nano" || implementation == "rsnano") {
            "Nano implementation must be 'nano' or 'rsnano'"
        }
        require(startupTimeout.isPositive()) { "startupTimeout must be positive" }
    }

    val image: String = if (implementation == "nano") NanoFixtures.NANO_IMAGE else NanoFixtures.RSNANO_IMAGE
    val storageProfile: String = "durable"

    suspend fun start(): NanoNodeEnvironment {
        val definition = definition()
        Files.createDirectories(storageRoot)
        val fileStoreType = Files.getFileStore(storageRoot).type().lowercase()
        require("tmpfs" !in fileStoreType && "ramfs" !in fileStoreType) {
            "Nano durable storage root $storageRoot is backed by $fileStoreType"
        }
        val dataDirectory = Files.createTempDirectory(storageRoot, "feeless-$implementation-")
        prepareDataDirectory(dataDirectory, definition)
        val container =
            NanoContainer(DockerImageName.parse(image)).apply {
                withExposedPorts(definition.rpcPort, definition.websocketPort)
                addFileSystemBind(
                    dataDirectory.toAbsolutePath().toString(),
                    definition.dataPath,
                    BindMode.READ_WRITE,
                    SelinuxContext.SHARED,
                )
                withCommand(*definition.command.toTypedArray())
                waitingFor(Wait.forListeningPorts(definition.rpcPort, definition.websocketPort))
                withStartupTimeout(startupTimeout.toJavaDuration())
            }

        try {
            withContext(Dispatchers.VIRTUAL) { container.start() }
            val rpcUrl = "http://${container.host}:${container.getMappedPort(definition.rpcPort)}"
            val websocketUrl = "ws://${container.host}:${container.getMappedPort(definition.websocketPort)}"
            verifyStartupAndInstallVotingKey(rpcUrl, definition)
            val adapter = NanoPostCementAdapter.connect(rpcUrl, websocketUrl)
            val digest =
                runCatching {
                    val imageId = container.containerInfo.imageId
                    container.dockerClient
                        .inspectImageCmd(imageId)
                        .exec()
                        .repoDigests
                        ?.firstOrNull()
                        ?.substringAfterLast('@')
                }.getOrNull() ?: container.containerInfo.imageId
            return NanoNodeEnvironment(
                implementation = implementation,
                imageReference = image,
                imageDigest = digest,
                storageProfile = storageProfile,
                rpcUrl = rpcUrl,
                websocketUrl = websocketUrl,
                adapter = adapter,
                container = container,
                dataDirectory = dataDirectory,
                containerDataPath = definition.dataPath,
            )
        } catch (error: Throwable) {
            val logs = runCatching { container.logs.takeLast(8_000) }.getOrDefault("")
            runCatching { withContext(Dispatchers.VIRTUAL) { container.stop() } }
            runCatching { dataDirectory.toFile().deleteRecursively() }
            if (error is CancellationException) throw error
            throw IllegalStateException("$implementation node startup failed: ${error.message}\n$logs", error)
        }
    }

    private suspend fun verifyStartupAndInstallVotingKey(
        rpcUrl: String,
        definition: NanoNodeDefinition,
    ) {
        val client = nanoRpcHttpClient()
        val rpc = NanoRpcClient(client, rpcUrl, Json { ignoreUnknownKeys = true })
        try {
            withTimeout(startupTimeout) {
                val version = awaitVersion(rpc)
                verifyVersion(version, definition)
                val accountInfo =
                    rpc.call(
                        "account_info",
                        mapOf(
                            "account" to JsonPrimitive(NanoFixtures.DEV_GENESIS_ACCOUNT),
                            "representative" to JsonPrimitive("true"),
                        ),
                    )
                check(accountInfo["frontier"]?.jsonPrimitive?.content == NanoFixtures.DEV_GENESIS_HASH) {
                    "fresh node has the wrong dev genesis frontier: $accountInfo"
                }
                val wallet =
                    rpc.call("wallet_create")["wallet"]?.jsonPrimitive?.content
                        ?: error("wallet_create did not return a wallet")
                val added =
                    rpc.call(
                        "wallet_add",
                        mapOf(
                            "wallet" to JsonPrimitive(wallet),
                            "key" to JsonPrimitive(NanoFixtures.DEV_GENESIS_PRIVATE_KEY),
                            "work" to JsonPrimitive("false"),
                        ),
                    )
                check(added["account"]?.jsonPrimitive?.content == NanoFixtures.DEV_GENESIS_ACCOUNT) {
                    "canonical dev genesis voting key mismatch: $added"
                }
                val representatives =
                    rpc.call("representatives")["representatives"]?.jsonObject
                        ?: error("representatives RPC did not return representatives")
                val weight = representatives[NanoFixtures.DEV_GENESIS_ACCOUNT]?.jsonPrimitive?.content?.toBigIntegerOrNull()
                check(weight != null && weight.signum() > 0) { "dev genesis representative has no voting weight" }
            }
        } finally {
            client.close()
        }
    }

    private suspend fun awaitVersion(rpc: NanoRpc): JsonObject {
        while (true) {
            try {
                return rpc.call("version")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                delay(250.milliseconds)
            }
        }
    }

    private fun verifyVersion(
        version: JsonObject,
        definition: NanoNodeDefinition,
    ) {
        check(version["node_vendor"]?.jsonPrimitive?.content == definition.expectedVendor) {
            "unexpected node vendor: $version"
        }
        check(definition.expectedBuildCommit in (version["build_info"]?.jsonPrimitive?.content ?: "")) {
            "unexpected node build: $version"
        }
        check(version["network"]?.jsonPrimitive?.content == "dev") { "node is not on the dev network: $version" }
        check(version["network_identifier"]?.jsonPrimitive?.content == NanoFixtures.DEV_GENESIS_HASH) {
            "node has the wrong dev network identifier: $version"
        }
    }

    private fun definition(): NanoNodeDefinition =
        if (implementation == "nano") {
            NanoNodeDefinition(
                dataPath = "/root/NanoDev",
                rpcPort = 45000,
                websocketPort = 7078,
                expectedVendor = "Nano V28.2",
                expectedBuildCommit = "0d8eea4",
                command =
                    listOf(
                        "nano_node",
                        "daemon",
                        "--network",
                        "dev",
                        "--data_path",
                        "/root/NanoDev",
                        "--config",
                        "rpc.enable=true",
                        "--config",
                        "node.enable_voting=true",
                        "--config",
                        "node.websocket.enable=true",
                        "--config",
                        "node.websocket.address=::ffff:0.0.0.0",
                        "--config",
                        "node.websocket.port=7078",
                        "--rpcconfig",
                        "enable_control=true",
                        "--rpcconfig",
                        "address=::ffff:0.0.0.0",
                        "--disable_add_initial_peers",
                        "--disable_ongoing_bootstrap",
                        "--disable_legacy_bootstrap",
                        "--disable_lazy_bootstrap",
                        "--disable_wallet_bootstrap",
                        "--disable_rep_crawler",
                        "--disable_tcp_realtime",
                        "--disable_bootstrap_listener",
                        "--disable_bootstrap_bulk_pull_server",
                        "--disable_bootstrap_bulk_push_client",
                    ),
            )
        } else {
            NanoNodeDefinition(
                dataPath = "/home/nanocurrency/NanoDev",
                rpcPort = 45000,
                websocketPort = 47000,
                expectedVendor = "RsNano V3.1",
                expectedBuildCommit = "267e45a5555039d79dba3699c27c574926940681",
                command =
                    listOf(
                        "--network",
                        "dev",
                        "--data-path",
                        "/home/nanocurrency/NanoDev",
                        "node",
                        "run",
                        "--enable-voting",
                        "--disable-ongoing-bootstrap",
                        "--disable-rep-crawler",
                        "--disable-block-processor-republishing",
                    ),
            )
        }

    private fun prepareDataDirectory(
        directory: Path,
        definition: NanoNodeDefinition,
    ) {
        runCatching {
            Files.setPosixFilePermissions(directory, PosixFilePermission.entries.toSet())
        }
        if (implementation != "rsnano") return
        Files.writeString(directory.resolve("config-node.toml"), RSNANO_NODE_CONFIG)
        Files.writeString(directory.resolve("config-rpc.toml"), RSNANO_RPC_CONFIG)
        directory
            .toFile()
            .listFiles()
            .orEmpty()
            .forEach { file -> file.setReadable(true, false) }
    }

    private data class NanoNodeDefinition(
        val dataPath: String,
        val rpcPort: Int,
        val websocketPort: Int,
        val expectedVendor: String,
        val expectedBuildCommit: String,
        val command: List<String>,
    )

    private class NanoContainer(
        image: DockerImageName,
    ) : GenericContainer<NanoContainer>(image)

    private companion object {
        val RSNANO_NODE_CONFIG =
            """
            [rpc]
            enable = true

            [node]
            preconfigured_peers = []

            [node.websocket]
            enable = true
            address = "::ffff:0.0.0.0"
            port = 47000

            [node.bootstrap]
            enable = false

            [node.lmdb]
            sync = "always"
            """.trimIndent() + "\n"

        val RSNANO_RPC_CONFIG =
            """
            address = "::ffff:0.0.0.0"
            port = 45000
            enable_control = true
            """.trimIndent() + "\n"
    }
}

class NanoNodeEnvironment internal constructor(
    val implementation: String,
    val imageReference: String,
    val imageDigest: String,
    val storageProfile: String,
    val rpcUrl: String,
    val websocketUrl: String,
    val adapter: NanoPostCementAdapter,
    private val container: GenericContainer<*>,
    private val dataDirectory: Path,
    private val containerDataPath: String,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var failure: Throwable? = null

        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error else previous.addSuppressed(error)
            }
        }

        attempt(adapter::close)
        runCatching { makeDataDirectoryHostAccessible() }
        attempt(container::stop)
        attempt {
            NanoDataDirectoryCleanup.delete(dataDirectory) { inaccessibleDirectory ->
                repairDataDirectoryPermissions(inaccessibleDirectory)
            }
        }
        failure?.let { throw it }
    }

    private fun makeDataDirectoryHostAccessible() {
        val result = container.execInContainer("chmod", "-R", "a+rwX", containerDataPath)
        check(result.exitCode == 0) {
            "failed to make Nano data directory host-accessible: ${result.stderr}"
        }
    }

    private fun repairDataDirectoryPermissions(directory: Path) {
        val cleanupContainer =
            NanoCleanupContainer(DockerImageName.parse(imageReference)).apply {
                addFileSystemBind(
                    directory.toAbsolutePath().toString(),
                    CLEANUP_MOUNT_PATH,
                    BindMode.READ_WRITE,
                    SelinuxContext.SHARED,
                )
                withCreateContainerCmdModifier { command ->
                    command.withEntrypoint("/bin/sh")
                    command.withUser("0:0")
                }
                withCommand("-c", "chmod -R a+rwX $CLEANUP_MOUNT_PATH")
                withStartupCheckStrategy(OneShotStartupCheckStrategy())
                withStartupTimeout(java.time.Duration.ofSeconds(30))
            }
        var failure: Throwable? = null
        try {
            cleanupContainer.start()
        } catch (error: Throwable) {
            failure = IllegalStateException("failed to repair Nano data directory permissions for $directory", error)
        } finally {
            try {
                cleanupContainer.stop()
            } catch (stopError: Throwable) {
                val previous = failure
                if (previous == null) failure = stopError else previous.addSuppressed(stopError)
            }
        }
        failure?.let { throw it }
    }

    private class NanoCleanupContainer(
        image: DockerImageName,
    ) : GenericContainer<NanoCleanupContainer>(image)

    private companion object {
        const val CLEANUP_MOUNT_PATH = "/nano-data-cleanup"
    }
}
