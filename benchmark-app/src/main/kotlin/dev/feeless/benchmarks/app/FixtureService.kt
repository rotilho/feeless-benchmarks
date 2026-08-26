package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.atto.AttoFixtures
import dev.feeless.benchmarks.nano.NanoFixtures
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

internal class FixtureService(
    private val report: (String) -> Unit = ::println,
) {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            explicitNulls = false
        }

    suspend fun generate(command: Command.GenerateFixtures) {
        val destination = command.fixturesDirectory.toAbsolutePath().normalize()
        val stagingParent = destination.parent ?: Path.of(".").toAbsolutePath()
        Files.createDirectories(stagingParent)
        val staging = Files.createTempDirectory(stagingParent, ".fixture-generation-")
        val first = Files.createDirectory(staging.resolve("first"))
        val second = Files.createDirectory(staging.resolve("second"))
        try {
            generateInto(command.implementation, first)
            validate(command.implementation, first)
            generateInto(command.implementation, second)
            validate(command.implementation, second)
            val promoted = FixturePromotion.requireIdenticalAndPromote(first, second, destination)
            promoted.forEach { path -> report("promoted ${path.toAbsolutePath()}") }
        } finally {
            deleteTree(staging)
        }
    }

    suspend fun validate(command: Command.ValidateFixtures) {
        validate(FixtureImplementation.ALL, command.fixturesDirectory.toAbsolutePath().normalize())
    }

    private suspend fun generateInto(
        implementation: FixtureImplementation,
        directory: Path,
    ) {
        if (implementation == FixtureImplementation.ALL || implementation == FixtureImplementation.ATTO) {
            AttoFixtures.generateCanonical(directory)
        }
        if (implementation == FixtureImplementation.ALL || implementation == FixtureImplementation.NANO) {
            NanoFixtures.generateCanonical(directory)
        }
    }

    private suspend fun validate(
        implementation: FixtureImplementation,
        directory: Path,
    ) {
        if (implementation == FixtureImplementation.ALL || implementation == FixtureImplementation.ATTO) {
            AttoFixtures.validateCanonical(directory).forEach { validation ->
                report(json.encodeToString(validation))
                check(validation.valid) { "invalid Atto fixture ${validation.fixture}" }
            }
        }
        if (implementation == FixtureImplementation.ALL || implementation == FixtureImplementation.NANO) {
            NanoFixtures.canonicalSpecs.forEach { spec ->
                val name = spec.fixtureFileName
                val validation = NanoFixtures.validateWithVerification(spec, directory.resolve(name))
                report(NanoFixtures.encodeValidation(validation).trimEnd())
                check(validation.valid) { "invalid Nano fixture ${validation.fixture ?: name}" }
            }
        }
    }

    private fun deleteTree(root: Path) {
        if (Files.notExists(root)) return
        Files.walk(root).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
