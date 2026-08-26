package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.CpuDetails
import dev.feeless.benchmarks.core.JavaRuntimeDetails
import dev.feeless.benchmarks.core.OperatingSystemDetails
import dev.feeless.benchmarks.core.RunManifest
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal object RunManifestFactory {
    fun create(
        repositoryRoot: Path,
        fixturePaths: List<Path>,
        imageDigests: Map<String, String>,
        runtimeConfiguration: Map<String, String>,
    ): RunManifest =
        RunManifest(
            runnerRevision = runnerRevision(repositoryRoot),
            fixtureHashes = fixturePaths.associate { path -> path.fileName.toString() to sha256(path) },
            imageDigests = imageDigests,
            java =
                JavaRuntimeDetails(
                    version = System.getProperty("java.version"),
                    vendor = System.getProperty("java.vendor"),
                    vmName = System.getProperty("java.vm.name"),
                    vmVersion = System.getProperty("java.vm.version"),
                ),
            operatingSystem =
                OperatingSystemDetails(
                    name = System.getProperty("os.name"),
                    version = System.getProperty("os.version"),
                    architecture = System.getProperty("os.arch"),
                ),
            cpu =
                CpuDetails(
                    logicalProcessorCount = Runtime.getRuntime().availableProcessors(),
                    model = cpuModel(),
                ),
            storageProfile = "durable",
            runtimeConfiguration = sanitize(runtimeConfiguration),
        )

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun runnerRevision(repositoryRoot: Path): String {
        val revision = git(repositoryRoot, "rev-parse", "HEAD") ?: "unknown"
        val status =
            git(
                repositoryRoot,
                "status",
                "--porcelain",
                "--",
                *RUNNER_PATHS.toTypedArray(),
            ) ?: return "unknown"
        val dirty = status.isNotEmpty()
        if (!dirty) return revision
        val worktreeHash = worktreeHash(repositoryRoot)
        return if (worktreeHash == null) "$revision-dirty" else "$revision-dirty-$worktreeHash"
    }

    private fun worktreeHash(repositoryRoot: Path): String? =
        runCatching {
            val command =
                listOf("git", "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--") +
                    RUNNER_PATHS
            val process =
                ProcessBuilder(command)
                    .directory(repositoryRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
            val listed = process.inputStream.use { it.readAllBytes() }
            if (process.waitFor() != 0) return@runCatching null

            val digest = MessageDigest.getInstance("SHA-256")
            listed
                .decodeToString()
                .split('\u0000')
                .filter(String::isNotEmpty)
                .sorted()
                .forEach { relative ->
                    val path = repositoryRoot.resolve(relative).normalize()
                    require(path.startsWith(repositoryRoot)) { "Git path escapes repository: $relative" }
                    digest.update(relative.encodeToByteArray())
                    digest.update(0.toByte())
                    if (Files.isSymbolicLink(path)) {
                        digest.update("<symlink>".encodeToByteArray())
                        digest.update(Files.readSymbolicLink(path).toString().encodeToByteArray())
                    } else if (Files.isRegularFile(path)) {
                        digest.update(if (Files.isExecutable(path)) "<file:x>".encodeToByteArray() else "<file>".encodeToByteArray())
                        Files.newInputStream(path).use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                            }
                        }
                    } else {
                        digest.update("<missing>".encodeToByteArray())
                    }
                    digest.update(0.toByte())
                }
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }.getOrNull()

    private fun git(
        repositoryRoot: Path,
        vararg arguments: String,
    ): String? =
        runCatching {
            val process =
                ProcessBuilder(listOf("git", *arguments))
                    .directory(repositoryRoot.toFile())
                    .redirectErrorStream(true)
                    .start()
            val output =
                process.inputStream
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
            output.takeIf { process.waitFor() == 0 }
        }.getOrNull()

    private fun cpuModel(): String? =
        runCatching {
            Files.lines(Path.of("/proc/cpuinfo")).use { lines ->
                lines
                    .filter { it.startsWith("model name") || it.startsWith("Hardware") }
                    .findFirst()
                    .orElse(null)
                    ?.substringAfter(':')
                    ?.trim()
            }
        }.getOrNull()

    internal fun sanitize(configuration: Map<String, String>): Map<String, String> =
        configuration
            .toSortedMap()
            .mapValues { (key, value) ->
                if (
                    sensitiveKeyFragments.any { fragment -> key.contains(fragment, ignoreCase = true) } ||
                    value.contains(CREDENTIAL_BEARING_URI)
                ) {
                    "<redacted>"
                } else {
                    value
                }
            }

    private val sensitiveKeyFragments =
        listOf(
            "api_key",
            "api-key",
            "apikey",
            "authorization",
            "cookie",
            "credential",
            "passphrase",
            "password",
            "private",
            "secret",
            "session",
            "token",
        )
    private val CREDENTIAL_BEARING_URI = Regex("://[^/@:\\s]+:[^/@\\s]+@")
    private val RUNNER_PATHS =
        listOf(
            "benchmark-app",
            "benchmark-atto",
            "benchmark-core",
            "benchmark-nano",
            "build.gradle.kts",
            "settings.gradle.kts",
            "gradle.properties",
            "gradle",
            "gradlew",
            "gradlew.bat",
        )
}
