package dev.feeless.benchmarks.nano

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanoFixturesTest {
    @Test
    fun `canonical fixtures are serial and 500 account workloads`() {
        // Given
        val expected =
            listOf(
                NanoFixtureSpec(fixture = "nano-serial", sourceCount = 1, blocksPerSource = 1_000),
                NanoFixtureSpec(fixture = "nano-500", sourceCount = 500, blocksPerSource = 100),
            )

        // When
        val actual = NanoFixtures.canonicalSpecs

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun `round robin generation advances every source once per sequence`() {
        // Given
        val generationOrder = mutableListOf<String>()

        // When
        val lanes =
            generateRoundRobinLanes(listOf("source-0", "source-1", "source-2"), itemsPerSource = 3) { source, sequence ->
                "$source:$sequence".also { generationOrder += it }
            }

        // Then
        assertEquals(
            listOf(
                "source-0:0",
                "source-1:0",
                "source-2:0",
                "source-0:1",
                "source-1:1",
                "source-2:1",
                "source-0:2",
                "source-1:2",
                "source-2:2",
            ),
            generationOrder,
        )
        assertEquals(
            listOf(
                listOf("source-0:0", "source-0:1", "source-0:2"),
                listOf("source-1:0", "source-1:1", "source-1:2"),
                listOf("source-2:0", "source-2:1", "source-2:2"),
            ),
            lanes,
        )
    }

    @Test
    fun `round robin fixture generation keeps ordered lanes and byte-identical output`() {
        // Given
        val firstDirectory = Files.createTempDirectory("nano-fixture-first-")
        val secondDirectory = Files.createTempDirectory("nano-fixture-second-")
        val first = firstDirectory.resolve("small.json")
        val second = secondDirectory.resolve("small.json")

        try {
            // When
            val firstGeneration = NanoFixtures.generate("small", 3, 4, first)
            val secondGeneration = NanoFixtures.generate("small", 3, 4, second)
            val fixture = Json.decodeFromString<NanoFixture>(first.readText())

            // Then
            assertTrue(firstGeneration.validation.valid)
            assertTrue(firstGeneration.validation.checksumsValid)
            assertEquals(3, firstGeneration.validation.sourceCount)
            assertEquals(4, firstGeneration.validation.setupCount)
            assertEquals(12, firstGeneration.validation.measuredCount)
            assertEquals(fixture.sourceAccounts.map { it.account }, fixture.measuredLanes.map { it.account })
            assertEquals(listOf(0, 1, 2), fixture.measuredLanes.map { it.sourceIndex })
            assertTrue(fixture.measuredLanes.all { lane -> lane.blocks.map { it.sequence } == (0..3).toList() })
            assertContentEquals(first.readBytes(), second.readBytes())
            assertContentEquals(
                firstGeneration.verificationPath.readBytes(),
                secondGeneration.verificationPath.readBytes(),
            )
        } finally {
            firstDirectory.toFile().deleteRecursively()
            secondDirectory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fixture records schema provenance threshold profile and checksums`() {
        // Given
        val directory = Files.createTempDirectory("nano-fixture-metadata-")
        val path = directory.resolve("small.json")

        try {
            // When
            NanoFixtures.generate("small", 1, 1, path)
            val root = Json.parseToJsonElement(path.readText()).jsonObject

            // Then
            assertEquals(NanoFixtures.SCHEMA, root.getValue("schema").jsonPrimitive.content)
            assertEquals(
                "ce2bf78a321ee98764117de5dcc230a7466c2502",
                root
                    .getValue("generator")
                    .jsonObject
                    .getValue("jnano_revision")
                    .jsonPrimitive.content,
            )
            assertEquals(
                NanoWork.EPOCH_0_AND_1,
                root
                    .getValue("threshold_profile")
                    .jsonObject
                    .getValue("epoch_0_and_1")
                    .jsonPrimitive.content,
            )
            assertTrue(
                root.getValue("checksums").jsonObject.keys.containsAll(
                    setOf("source_accounts_sha256", "setup_blocks_sha256", "measured_lanes_sha256"),
                ),
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `tampered fixture returns a structured validation failure`() {
        // Given
        val directory = Files.createTempDirectory("nano-fixture-tamper-")
        val path = directory.resolve("small.json")

        try {
            NanoFixtures.generate("small", 1, 1, path)
            path.writeText(path.readText().replace(NanoWork.EPOCH_0_AND_1, "FF00000000000000"))

            // When
            val validation = NanoFixtures.validate(path)

            // Then
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { "threshold profile" in it })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `canonical validation rejects a missing or stale verification artifact`() {
        // Given
        val directory = Files.createTempDirectory("nano-fixture-verification-")
        val path = directory.resolve("small.json")
        val verification = directory.resolve("small-verification.json")

        try {
            NanoFixtures.generate("small", 1, 1, path)
            Files.writeString(verification, "{}\n")

            // When
            val stale = NanoFixtures.validateWithVerification(path)
            Files.delete(verification)
            val missing = NanoFixtures.validateWithVerification(path)

            // Then
            assertFalse(stale.valid)
            assertTrue(stale.errors.any { "does not match" in it })
            assertFalse(missing.valid)
            assertTrue(missing.errors.any { "is missing" in it })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `canonical validation rejects a self-consistent fixture with the wrong shape`() {
        // Given
        val directory = Files.createTempDirectory("nano-fixture-shape-")
        val path = directory.resolve("expected.json")

        try {
            NanoFixtures.generate("expected", sourceCount = 2, blocksPerSource = 3, path = path)
            val expected = NanoFixtureSpec("expected", sourceCount = 3, blocksPerSource = 2)

            // When
            val validation = NanoFixtures.validateWithVerification(expected, path)

            // Then
            assertFalse(validation.valid)
            assertTrue(validation.errors.any { "expected 3" in it })
            assertTrue(validation.errors.any { "lane distribution" in it })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scenario loader preserves setup order lanes and predicted hashes`() {
        // Given
        val directory = Files.createTempDirectory("nano-fixture-scenario-")
        val path = directory.resolve("small.json")

        try {
            NanoFixtures.generate("small", 2, 3, path)

            // When
            val scenario = NanoFixtures.loadScenario(path, "rsnano")

            // Then
            assertEquals("rsnano", scenario.implementation)
            assertEquals("small", scenario.fixture)
            assertEquals(2, scenario.setup.size)
            assertEquals(listOf(3, 3), scenario.lanes.values.map { it.size })
            assertEquals(6, scenario.expectedCount)
            assertEquals(
                (0..2).toList(),
                scenario.lanes.values
                    .first()
                    .map { it.sequence },
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `fixture generation refuses every overwrite`() {
        // Given
        val directory = Files.createTempDirectory("nano-fixture-overwrite-")
        val path = directory.resolve("small.json")

        try {
            NanoFixtures.generate("small", 1, 1, path)

            // When / Then
            assertFailsWith<IllegalArgumentException> { NanoFixtures.generate("small", 1, 1, path) }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
