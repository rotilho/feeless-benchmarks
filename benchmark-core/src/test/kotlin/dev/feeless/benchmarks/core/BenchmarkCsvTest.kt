package dev.feeless.benchmarks.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BenchmarkCsvTest {
    @Test
    fun `CSV preserves the established raw field order and blank nullable fields`() {
        // Given
        val samples =
            listOf(
                sample(sequence = 1, start = 10, completion = 30, latency = 20, error = null),
                sample(
                    sequence = 2,
                    start = 11,
                    completion = null,
                    latency = null,
                    error = "TimeoutException: late, retry",
                ),
            )

        // When
        val csv = encodeSamplesCsv(samples)

        // Then
        assertEquals(
            "implementation,fixture,lane,sequence,account,hash,start_monotonic_ns," +
                "completion_monotonic_ns,latency_ns,error\r\n" +
                "node,fixture,a,1,acct-a,hash-1,10,30,20,\r\n" +
                "node,fixture,a,2,acct-a,hash-2,11,,,\"TimeoutException: late, retry\"\r\n",
            csv,
        )
        assertTrue(
            csv
                .lines()
                .first()
                .split(',')
                .containsAll(RAW_SAMPLE_FIELDS),
        )
    }

    @Test
    fun `CSV round trip preserves quoted commas quotes and newlines`() {
        // Given
        val samples =
            listOf(
                BenchmarkSample(
                    implementation = "node,variant",
                    fixture = "fixture\"quoted",
                    lane = "lane\r\nnext",
                    sequence = 7,
                    account = "account",
                    hash = "hash",
                    startMonotonicNs = 10,
                    completionMonotonicNs = null,
                    latencyNs = null,
                    error = "failure, \"quoted\"\nnext",
                ),
            )

        // When
        val decoded = decodeSamplesCsv(encodeSamplesCsv(samples))

        // Then
        assertEquals(samples, decoded)
    }

    @Test
    fun `CSV decoder accepts existing LF terminated content`() {
        // Given
        val csv =
            RAW_SAMPLE_FIELDS.joinToString(",") + "\n" +
                "node,fixture,a,1,account,hash,10,30,20,\n"

        // When
        val sample = decodeSamplesCsv(csv).single()

        // Then
        assertEquals(10, sample.startMonotonicNs)
        assertEquals(30, sample.completionMonotonicNs)
        assertEquals(20, sample.latencyNs)
        assertEquals(null, sample.error)
    }

    @Test
    fun `CSV decoder rejects incompatible headers and malformed rows`() {
        // Given
        val wrongHeader = "implementation,fixture\r\nnode,fixture\r\n"
        val malformedQuote = RAW_SAMPLE_FIELDS.joinToString(",") + "\r\n\"unterminated"
        val invalidSequence =
            RAW_SAMPLE_FIELDS.joinToString(",") + "\r\n" +
                "node,fixture,a,nope,account,hash,10,30,20,\r\n"

        // When
        val headerFailure = assertFailsWith<IllegalArgumentException> { decodeSamplesCsv(wrongHeader) }
        val quoteFailure = assertFailsWith<IllegalArgumentException> { decodeSamplesCsv(malformedQuote) }
        val numberFailure = assertFailsWith<IllegalArgumentException> { decodeSamplesCsv(invalidSequence) }

        // Then
        assertTrue(headerFailure.message.orEmpty().startsWith("samples CSV header must be"))
        assertEquals("unterminated quoted field in samples CSV", quoteFailure.message)
        assertEquals("invalid sequence in samples CSV row 2: nope", numberFailure.message)
    }

    @Test
    fun `Kotlin statistics exactly recalculate every available canonical result`() {
        // Given
        val repositoryRoot = Path.of(requireNotNull(System.getProperty("repositoryRoot")))
        val resultsRoot = acceptedResultsRoot(repositoryRoot)
        val explicitResultsRoot = System.getProperty("acceptedResultsRoot") != null
        val availableResults = canonicalResults.filter { result -> Files.isDirectory(result.directory(resultsRoot)) }

        // When
        val recalculated =
            availableResults.associateWith { result ->
                BenchmarkStatistics.summarize(readSamplesCsv(result.samplesPath(resultsRoot)))
            }
        val recorded =
            availableResults.associateWith { result ->
                readSummaryJson(result.summaryPath(resultsRoot))
            }

        // Then
        assertTrue(availableResults.isNotEmpty(), "no canonical result directories are available")
        if (explicitResultsRoot) {
            assertEquals(canonicalResults, availableResults, "the candidate result root must contain all canonical scenarios")
        }
        assertEquals(recorded, recalculated)
        availableResults.forEach { result ->
            val samples = readSamplesCsv(result.samplesPath(resultsRoot))
            val summary = recorded.getValue(result)
            assertEquals(result.expectedCount, summary.sampleCount, result.stem)
            assertEquals(result.expectedCount, summary.successCount, result.stem)
            assertEquals(0, summary.errorCount, result.stem)
            assertEquals(setOf(result.implementation), samples.map(BenchmarkSample::implementation).toSet(), result.stem)
            assertEquals(setOf(result.fixture), samples.map(BenchmarkSample::fixture).toSet(), result.stem)
        }
    }

    @Test
    fun `every available Kotlin result has complete durable provenance`() {
        // Given
        val repositoryRoot = Path.of(requireNotNull(System.getProperty("repositoryRoot")))
        val resultsRoot = acceptedResultsRoot(repositoryRoot)
        val availableResults = canonicalResults.filter { result -> Files.isDirectory(result.directory(resultsRoot)) }
        val explicitResultsRoot = System.getProperty("acceptedResultsRoot") != null

        // When / Then
        if (explicitResultsRoot) {
            assertEquals(canonicalResults, availableResults, "the candidate result root must contain all canonical scenarios")
        }
        availableResults.forEach { result ->
            val manifestPath = result.manifestPath(resultsRoot)
            assertTrue(Files.isRegularFile(manifestPath), "${result.stem} is missing its run manifest")
            val manifest = readRunManifestJson(manifestPath)
            assertTrue(
                manifest.runnerRevision.isNotBlank() && manifest.runnerRevision != "unknown",
                "${result.stem} has no runner revision",
            )
            assertEquals(result.fixtureHashes(repositoryRoot), manifest.fixtureHashes, result.stem)
            assertEquals(result.imageReferences, manifest.imageDigests.keys, result.stem)
            assertTrue(
                manifest.imageDigests.values.all(IMAGE_DIGEST::matches),
                "${result.stem} contains an invalid image digest",
            )
            assertEquals("durable", manifest.storageProfile, result.stem)
            assertEquals(result.fixture, manifest.runtimeConfiguration["fixture"], "${result.stem} runtime fixture")
        }
    }

    private fun sample(
        sequence: Int,
        start: Long,
        completion: Long?,
        latency: Long?,
        error: String?,
    ): BenchmarkSample =
        BenchmarkSample(
            implementation = "node",
            fixture = "fixture",
            lane = "a",
            sequence = sequence,
            account = "acct-a",
            hash = "hash-$sequence",
            startMonotonicNs = start,
            completionMonotonicNs = completion,
            latencyNs = latency,
            error = error,
        )

    private fun acceptedResultsRoot(repositoryRoot: Path): Path =
        System
            .getProperty("acceptedResultsRoot")
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: repositoryRoot.resolve("results/common-runner")

    private data class CanonicalResult(
        val stem: String,
        val implementation: String,
        val fixture: String,
        val expectedCount: Int,
        val fixtureFiles: Set<String>,
        val imageReferences: Set<String>,
    ) {
        fun directory(resultsRoot: Path): Path = resultsRoot.resolve(stem)

        fun samplesPath(resultsRoot: Path): Path = directory(resultsRoot).resolve("$stem-samples.csv")

        fun summaryPath(resultsRoot: Path): Path = directory(resultsRoot).resolve("$stem-summary.json")

        fun manifestPath(resultsRoot: Path): Path = directory(resultsRoot).resolve("$stem-manifest.json")

        fun fixtureHashes(repositoryRoot: Path): Map<String, String> =
            fixtureFiles.associateWith { fixtureFile -> sha256(repositoryRoot.resolve("fixtures/$fixtureFile")) }
    }

    private companion object {
        val IMAGE_DIGEST = Regex("sha256:[0-9a-f]{64}")
        val canonicalResults =
            listOf(
                CanonicalResult(
                    stem = "nano-serial",
                    implementation = "nano",
                    fixture = "nano-serial",
                    expectedCount = 1_000,
                    fixtureFiles = setOf("nano-serial.json"),
                    imageReferences = setOf("nanocurrency/nano:V28.2"),
                ),
                CanonicalResult(
                    stem = "nano-500",
                    implementation = "nano",
                    fixture = "nano-500",
                    expectedCount = 50_000,
                    fixtureFiles = setOf("nano-500.json"),
                    imageReferences = setOf("nanocurrency/nano:V28.2"),
                ),
                CanonicalResult(
                    stem = "rsnano-serial",
                    implementation = "rsnano",
                    fixture = "nano-serial",
                    expectedCount = 1_000,
                    fixtureFiles = setOf("nano-serial.json"),
                    imageReferences = setOf("rsnano/rsnano:V3.1"),
                ),
                CanonicalResult(
                    stem = "rsnano-500",
                    implementation = "rsnano",
                    fixture = "nano-500",
                    expectedCount = 50_000,
                    fixtureFiles = setOf("nano-500.json"),
                    imageReferences = setOf("rsnano/rsnano:V3.1"),
                ),
                CanonicalResult(
                    stem = "atto-serial",
                    implementation = "atto",
                    fixture = "atto-serial",
                    expectedCount = 1_000,
                    fixtureFiles = setOf("atto-serial-initial.zip", "atto-serial-benchmark.zip"),
                    imageReferences = setOf("ghcr.io/attocash/node:1.34-live", "mysql:8.4"),
                ),
                CanonicalResult(
                    stem = "atto-500",
                    implementation = "atto",
                    fixture = "atto-500",
                    expectedCount = 50_000,
                    fixtureFiles = setOf("atto-500-initial.zip", "atto-500-benchmark.zip"),
                    imageReferences = setOf("ghcr.io/attocash/node:1.34-live", "mysql:8.4"),
                ),
            )

        fun sha256(path: Path): String {
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
    }
}
