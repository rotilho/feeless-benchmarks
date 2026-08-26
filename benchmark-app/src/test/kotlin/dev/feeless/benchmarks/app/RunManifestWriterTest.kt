package dev.feeless.benchmarks.app

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunManifestWriterTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `redacts sensitive runtime configuration`() {
        // Given
        val configuration =
            mapOf(
                "implementation" to "atto",
                "databasePassword" to "not-for-output",
                "private_key" to "not-for-output",
                "api_key" to "not-for-output",
                "database.uri" to "mysql://user:not-for-output@database/atto",
            )

        // When
        val sanitized = RunManifestFactory.sanitize(configuration)

        // Then
        assertEquals("atto", sanitized["implementation"])
        assertEquals("<redacted>", sanitized["databasePassword"])
        assertEquals("<redacted>", sanitized["private_key"])
        assertEquals("<redacted>", sanitized["api_key"])
        assertEquals("<redacted>", sanitized["database.uri"])
    }

    @Test
    fun `manifest records fixture image runtime and durable storage evidence`() {
        // Given
        val fixture = temporaryDirectory.resolve("fixture.json")
        Files.writeString(fixture, "fixture\n")

        // When
        val manifest =
            RunManifestFactory.create(
                repositoryRoot = temporaryDirectory,
                fixturePaths = listOf(fixture),
                imageDigests = mapOf("node:1" to "sha256:abc"),
                runtimeConfiguration = mapOf("network" to "dev"),
            )

        // Then
        assertEquals("unknown", manifest.runnerRevision)
        assertEquals(64, manifest.fixtureHashes.getValue("fixture.json").length)
        assertEquals(mapOf("node:1" to "sha256:abc"), manifest.imageDigests)
        assertEquals("durable", manifest.storageProfile)
        assertEquals("dev", manifest.runtimeConfiguration["network"])
        assertTrue(manifest.cpu.logicalProcessorCount > 0)
    }

    @Test
    fun `dirty runner revision includes a reproducible source tree hash`() {
        // Given
        val source = temporaryDirectory.resolve("benchmark-app/src/main/kotlin/Runner.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "class Runner\n")
        git("init")
        git("add", ".")
        git("-c", "user.name=Benchmark Test", "-c", "user.email=benchmark@example.invalid", "commit", "-m", "initial")
        val clean = manifest(source).runnerRevision

        // When
        Files.writeString(source, "class ChangedRunner\n")
        val firstDirty = manifest(source).runnerRevision
        val secondDirty = manifest(source).runnerRevision

        // Then
        assertTrue(Regex("${Regex.escape(clean)}-dirty-[0-9a-f]{64}").matches(firstDirty))
        assertEquals(firstDirty, secondDirty)
    }

    private fun manifest(fixture: Path) =
        RunManifestFactory.create(
            repositoryRoot = temporaryDirectory,
            fixturePaths = listOf(fixture),
            imageDigests = emptyMap(),
            runtimeConfiguration = emptyMap(),
        )

    private fun git(vararg arguments: String) {
        val process =
            ProcessBuilder(listOf("git", *arguments))
                .directory(temporaryDirectory.toFile())
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "git ${arguments.joinToString(" ")} failed: $output" }
    }
}
