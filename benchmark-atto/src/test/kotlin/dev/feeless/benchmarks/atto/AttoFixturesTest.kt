@file:OptIn(ExperimentalTime::class)

package dev.feeless.benchmarks.atto

import cash.atto.commons.AttoTransaction
import cash.atto.commons.toAtto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AttoFixturesTest {
    @Test
    fun `loads fixture artifacts into ordered setup and account lanes`() {
        // Given
        val fixtures = Path.of(System.getProperty("fixturesDirectory"))
        val cases =
            listOf(
                FixtureShape("atto-genesis", setupCount = 0, laneCount = 1, laneSize = 10_000, expectedCount = 10_000),
                FixtureShape("atto-100", setupCount = 198, laneCount = 100, laneSize = 100, expectedCount = 10_000),
                FixtureShape("atto-serial", setupCount = 0, laneCount = 1, laneSize = 1_000, expectedCount = 1_000),
                FixtureShape("atto-500", setupCount = 998, laneCount = 500, laneSize = 100, expectedCount = 50_000),
            )

        for (case in cases) {
            // When
            val scenario =
                AttoFixtures.loadScenario(
                    fixture = case.fixture,
                    initialPath = fixtures.resolve("${case.fixture}-initial.zip"),
                    benchmarkPath = fixtures.resolve("${case.fixture}-benchmark.zip"),
                )

            // Then
            assertEquals("atto", scenario.implementation)
            assertEquals(case.fixture, scenario.fixture)
            assertEquals(case.expectedCount, scenario.expectedCount)
            assertEquals(case.setupCount, scenario.setup.size)
            assertEquals(case.laneCount, scenario.lanes.size)
            assertEquals(
                setOf(case.laneSize),
                scenario.lanes.values
                    .map { it.size }
                    .toSet(),
            )
            assertEquals((1..case.setupCount).toList(), scenario.setup.map { it.sequence })
            for ((account, lane) in scenario.lanes) {
                assertEquals((1..case.laneSize).toList(), lane.map { it.sequence })
                assertTrue(lane.all { it.lane == account && it.account == account })
                val heights =
                    lane.map {
                        it.payload
                            .transaction()
                            .height.value
                    }
                assertEquals(heights.first(), heights.last() - (case.laneSize - 1).toULong())
            }
        }
    }

    @Test
    fun `generation is byte-identical and produces cryptographically valid fixtures`() =
        runBlocking {
            // Given
            val spec =
                AttoFixtureSpec(
                    fixture = "atto-small",
                    accountCount = 2,
                    transactionCount = 4,
                    seed = 99,
                    baseTimestamp = "2026-01-01T00:00:00Z",
                )
            val firstDirectory = createTempDirectory("atto-first-")
            val secondDirectory = createTempDirectory("atto-second-")

            // When
            val firstValidation = AttoFixtures.generate(spec, firstDirectory)
            val secondValidation = AttoFixtures.generate(spec, secondDirectory)

            // Then
            assertTrue(firstValidation.valid, firstValidation.errors.joinToString())
            assertTrue(secondValidation.valid, secondValidation.errors.joinToString())
            assertEquals(firstValidation.validTransactionCount, firstValidation.expectedTransactionCount)
            assertEquals(1, firstValidation.generator.workSearchParallelism)
            assertContentEquals(
                Files.readAllBytes(firstDirectory.resolve(spec.initialFileName)),
                Files.readAllBytes(secondDirectory.resolve(spec.initialFileName)),
            )
            assertContentEquals(
                Files.readAllBytes(firstDirectory.resolve(spec.benchmarkFileName)),
                Files.readAllBytes(secondDirectory.resolve(spec.benchmarkFileName)),
            )
            assertContentEquals(
                Files.readAllBytes(firstDirectory.resolve(spec.verificationFileName)),
                Files.readAllBytes(secondDirectory.resolve(spec.verificationFileName)),
            )
            assertTrue(Json.encodeToString(firstValidation).contains("\"valid\":true"))
        }

    @Test
    fun `canonical fixtures share timestamp rounds and preserve lane order`() {
        // Given
        val fixtures = Path.of(System.getProperty("fixturesDirectory"))

        for (spec in AttoFixtures.canonicalSpecs) {
            val scenario =
                AttoFixtures.loadScenario(
                    fixture = spec.fixture,
                    initialPath = fixtures.resolve(spec.initialFileName),
                    benchmarkPath = fixtures.resolve(spec.benchmarkFileName),
                )
            val firstBenchmarkTimestamp = Instant.parse(spec.baseTimestamp) + (spec.accountCount * 2 + 1).milliseconds

            // When / Then
            scenario.lanes.values.forEach { lane ->
                lane.forEachIndexed { sequence, item ->
                    val expectedTimestamp = (firstBenchmarkTimestamp + sequence.milliseconds).toAtto()
                    assertEquals(
                        expectedTimestamp,
                        item.payload
                            .transaction()
                            .block.timestamp,
                    )
                }
            }
        }
    }

    @Test
    fun `canonical fixture verification artifacts are valid`() =
        runBlocking {
            // Given
            val fixtures = Path.of(System.getProperty("fixturesDirectory"))

            // When
            val validations = AttoFixtures.validateCanonical(fixtures)

            // Then
            assertEquals(AttoFixtures.canonicalSpecs.map { it.fixture }, validations.map { it.fixture })
            validations.forEach { validation ->
                assertTrue(validation.valid, validation.errors.joinToString())
            }
        }

    @Test
    fun `rejects ZIPs that are not one LF-terminated UTF-8 JSONL member`() {
        // Given
        val fixtures = Path.of(System.getProperty("fixturesDirectory"))
        val benchmark = fixtures.resolve("atto-genesis-benchmark.zip")
        val directory = createTempDirectory("atto-invalid-")
        val cases =
            mapOf(
                "multiple" to mapOf("one.jsonl" to "{}\n".encodeToByteArray(), "two.jsonl" to "{}\n".encodeToByteArray()),
                "wrong-suffix" to mapOf("transactions.json" to "{}\n".encodeToByteArray()),
                "missing-lf" to mapOf("transactions.jsonl" to "{}".encodeToByteArray()),
                "invalid-utf8" to mapOf("transactions.jsonl" to byteArrayOf(0xff.toByte(), '\n'.code.toByte())),
            )

        for ((name, entries) in cases) {
            val initial = directory.resolve("$name.zip")
            writeZip(initial, entries)

            // When / Then
            assertFails(name) {
                AttoFixtures.loadScenario("invalid", initial, benchmark)
            }
        }
    }

    @Test
    fun `validation reports a tampered signature`() =
        runBlocking {
            // Given
            val spec =
                AttoFixtureSpec(
                    fixture = "atto-tamper",
                    accountCount = 1,
                    transactionCount = 1,
                    seed = 7,
                    baseTimestamp = "2026-01-01T00:00:00Z",
                )
            val directory = createTempDirectory("atto-tamper-")
            AttoFixtures.generate(spec, directory)
            val originalInitial = directory.resolve(spec.initialFileName)
            val initialText = readOnlyZipMember(originalInitial).decodeToString()
            val signatureStart = initialText.indexOf("\"signature\":\"") + "\"signature\":\"".length
            val replacement = if (initialText[signatureStart] == '0') '1' else '0'
            val tamperedText = initialText.replaceRange(signatureStart, signatureStart + 1, replacement.toString())
            val tamperedInitial = directory.resolve("tampered-initial.zip")
            writeZip(tamperedInitial, mapOf("tampered-initial.jsonl" to tamperedText.encodeToByteArray()))

            // When
            val validation =
                AttoFixtures.validate(
                    spec,
                    tamperedInitial,
                    directory.resolve(spec.benchmarkFileName),
                )

            // Then
            assertEquals(false, validation.valid)
            assertEquals(validation.expectedTransactionCount - 1, validation.validTransactionCount)
            assertTrue(validation.errors.any { it.contains("Signature is invalid") })
        }

    private fun AttoPublication.transaction(): AttoTransaction = AttoTransaction.fromJson(copyEncodedTransaction().decodeToString())
}

private data class FixtureShape(
    val fixture: String,
    val setupCount: Int,
    val laneCount: Int,
    val laneSize: Int,
    val expectedCount: Int,
)

private fun writeZip(
    path: Path,
    entries: Map<String, ByteArray>,
) {
    ZipOutputStream(Files.newOutputStream(path)).use { zip ->
        for ((name, contents) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(contents)
            zip.closeEntry()
        }
    }
}

private fun readOnlyZipMember(path: Path): ByteArray =
    ZipFile(path.toFile()).use { archive ->
        val entry = archive.entries().nextElement()
        archive.getInputStream(entry).use { it.readAllBytes() }
    }
