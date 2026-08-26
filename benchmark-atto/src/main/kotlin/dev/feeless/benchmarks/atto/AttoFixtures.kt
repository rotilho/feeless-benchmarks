@file:OptIn(ExperimentalTime::class)

package dev.feeless.benchmarks.atto

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.PreviousSupport
import cash.atto.commons.toAtto
import dev.feeless.benchmarks.atto.generation.AttoTransactionGenerator
import dev.feeless.benchmarks.core.BenchmarkItem
import dev.feeless.benchmarks.core.BenchmarkScenario
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object AttoFixtures {
    val canonicalSpecs: List<AttoFixtureSpec> =
        listOf(
            AttoFixtureSpec(
                fixture = "atto-serial",
                accountCount = 1,
                transactionCount = 1_000,
                seed = 11_003,
                baseTimestamp = "2026-01-01T00:00:00Z",
            ),
            AttoFixtureSpec(
                fixture = "atto-500",
                accountCount = 500,
                transactionCount = 50_000,
                seed = 11_004,
                baseTimestamp = "2026-01-01T00:00:00Z",
            ),
        )

    suspend fun generateCanonical(outputDirectory: Path): List<AttoFixtureValidation> = canonicalSpecs.map { generate(it, outputDirectory) }

    suspend fun generate(
        spec: AttoFixtureSpec,
        outputDirectory: Path,
    ): AttoFixtureValidation {
        Files.createDirectories(outputDirectory)
        val generated = AttoTransactionGenerator(spec).use { it.generate() }
        val initialPath = outputDirectory.resolve(spec.initialFileName)
        val benchmarkPath = outputDirectory.resolve(spec.benchmarkFileName)
        writeZip(initialPath, generated.initialTransactions)
        writeZip(benchmarkPath, generated.benchmarkTransactions)
        val validation = validate(spec, initialPath, benchmarkPath)
        writeValidation(outputDirectory.resolve(spec.verificationFileName), validation)
        return validation
    }

    suspend fun validateCanonical(directory: Path): List<AttoFixtureValidation> =
        canonicalSpecs.map { spec ->
            validateWithVerification(
                spec = spec,
                initialPath = directory.resolve(spec.initialFileName),
                benchmarkPath = directory.resolve(spec.benchmarkFileName),
                verificationPath = directory.resolve(spec.verificationFileName),
            )
        }

    suspend fun validateWithVerification(
        spec: AttoFixtureSpec,
        initialPath: Path,
        benchmarkPath: Path,
        verificationPath: Path = initialPath.resolveSibling(spec.verificationFileName),
    ): AttoFixtureValidation {
        val validation = validate(spec, initialPath, benchmarkPath)
        val verificationError =
            when {
                !Files.isRegularFile(verificationPath) ->
                    "${spec.verificationFileName}: verification artifact is missing"

                !Files.readAllBytes(verificationPath).contentEquals(validation.encodedValidation()) ->
                    "${spec.verificationFileName}: verification artifact does not match the fixtures"

                else -> null
            }
        return if (verificationError == null) {
            validation
        } else {
            validation.copy(errors = validation.errors + verificationError, valid = false)
        }
    }

    suspend fun validate(
        spec: AttoFixtureSpec,
        initialPath: Path,
        benchmarkPath: Path,
    ): AttoFixtureValidation {
        val errors = mutableListOf<String>()
        val initialResult = runCatching { readTransactions(initialPath) }
        val benchmarkResult = runCatching { readTransactions(benchmarkPath) }
        initialResult.exceptionOrNull()?.let { errors += "${spec.initialFileName}: ${it.message}" }
        benchmarkResult.exceptionOrNull()?.let { errors += "${spec.benchmarkFileName}: ${it.message}" }
        val initial = initialResult.getOrDefault(emptyList())
        val benchmark = benchmarkResult.getOrDefault(emptyList())

        val expectedInitialCount = 1 + 2 * (spec.accountCount - 1)
        errors.requireThat(
            initial.size == expectedInitialCount,
            "Initial fixture has ${initial.size} transactions; expected $expectedInitialCount",
        )
        errors.requireThat(
            benchmark.size == spec.transactionCount,
            "Benchmark fixture has ${benchmark.size} transactions; expected ${spec.transactionCount}",
        )

        val genesis = initial.firstOrNull()?.transaction
        errors.requireThat(genesis?.block is AttoOpenBlock, "Initial fixture must start with an OPEN genesis transaction")
        errors.requireThat(genesis?.block?.network == AttoNetwork.LOCAL, "Genesis transaction must use the LOCAL network")
        errors.requireThat(
            genesis?.block?.publicKey?.toString() == GENESIS_PUBLIC_KEY,
            "Genesis transaction public key does not match the canonical voter key",
        )

        val lanes =
            benchmark.groupByTo(linkedMapOf()) {
                it.transaction.block.publicKey
                    .toString()
            }
        val laneSizes =
            lanes.values
                .map { it.size }
                .distinct()
                .sorted()
        errors.requireThat(lanes.size == spec.accountCount, "Fixture has ${lanes.size} lanes; expected ${spec.accountCount}")
        errors.requireThat(
            laneSizes == listOf(spec.transactionCount / spec.accountCount),
            "Fixture lane sizes are $laneSizes; expected ${spec.transactionCount / spec.accountCount}",
        )
        validateBenchmarkTimestamps(spec, lanes.values, errors)

        val benchmarkHashes = benchmark.map { it.transaction.hash.toString() }
        val uniqueBenchmarkHashes = benchmarkHashes.toSet().size
        errors.requireThat(
            uniqueBenchmarkHashes == spec.transactionCount,
            "Fixture has $uniqueBenchmarkHashes unique benchmark hashes; expected ${spec.transactionCount}",
        )
        val allHashes = (initial + benchmark).map { it.transaction.hash.toString() }
        errors.requireThat(
            allHashes.toSet().size == allHashes.size,
            "Initial and benchmark fixtures contain duplicate transaction hashes",
        )
        errors.requireThat(
            (initial + benchmark).all { it.transaction.block.network == AttoNetwork.LOCAL },
            "Every Atto fixture transaction must use the LOCAL network",
        )
        validateLedgerOrder(initial, benchmark, errors)

        var validTransactionCount = 0
        for (encoded in initial + benchmark) {
            val validation = encoded.transaction.validate()
            if (validation.isValid) {
                validTransactionCount++
            } else if (errors.size < MAX_REPORTED_ERRORS) {
                errors += "Transaction ${encoded.transaction.hash} is invalid: ${validation.getError()}"
            }
        }
        val expectedTransactionCount = expectedInitialCount + spec.transactionCount
        errors.requireThat(
            validTransactionCount == expectedTransactionCount,
            "$validTransactionCount/$expectedTransactionCount transactions passed signature and work validation",
        )

        return AttoFixtureValidation(
            fixture = spec.fixture,
            generator = spec.provenance(),
            initial = initialPath.validation(initial.size),
            benchmark = benchmarkPath.validation(benchmark.size),
            setupCount = (initial.size - 1).coerceAtLeast(0),
            laneCount = lanes.size,
            distinctTransactionsPerLane = laneSizes,
            uniqueBenchmarkHashes = uniqueBenchmarkHashes,
            validTransactionCount = validTransactionCount,
            expectedTransactionCount = expectedTransactionCount,
            errors = errors,
            valid = errors.isEmpty(),
        )
    }

    fun loadScenario(
        fixture: String,
        initialPath: Path,
        benchmarkPath: Path,
    ): BenchmarkScenario<AttoPublication> {
        val initial = readTransactions(initialPath)
        require(initial.isNotEmpty()) { "Atto initial fixture must contain the configured genesis" }
        val benchmark = readTransactions(benchmarkPath)

        val setup =
            initial.drop(1).mapIndexed { index, encoded ->
                encoded.toItem(lane = "setup", sequence = index + 1)
            }
        val lanes = linkedMapOf<String, MutableList<BenchmarkItem<AttoPublication>>>()
        for (encoded in benchmark) {
            val account =
                encoded.transaction.block.publicKey
                    .toString()
            val lane = lanes.getOrPut(account) { mutableListOf() }
            lane += encoded.toItem(lane = account, sequence = lane.size + 1)
        }

        return BenchmarkScenario(
            implementation = "atto",
            fixture = fixture,
            setup = setup,
            lanes = lanes,
            expectedCount = benchmark.size,
        )
    }

    internal fun readGenesis(initialPath: Path): AttoTransaction {
        val initial = readTransactions(initialPath)
        return initial.firstOrNull()?.transaction
            ?: throw IllegalArgumentException("Atto initial fixture must contain the configured genesis")
    }

    private fun readTransactions(path: Path): List<EncodedAttoTransaction> {
        val contents =
            ZipFile(path.toFile()).use { archive ->
                val entries = archive.entries().asSequence().toList()
                require(entries.size == 1 && !entries.single().isDirectory && entries.single().name.endsWith(".jsonl")) {
                    "Atto fixture ZIP must contain exactly one .jsonl member"
                }
                archive.getInputStream(entries.single()).use { it.readAllBytes() }
            }
        require(contents.isNotEmpty() && contents.last() == '\n'.code.toByte()) {
            "Atto fixture JSONL must be LF-terminated"
        }

        val transactions = mutableListOf<EncodedAttoTransaction>()
        var lineStart = 0
        for (index in contents.indices) {
            if (contents[index] != '\n'.code.toByte()) continue
            val bytes = contents.copyOfRange(lineStart, index)
            lineStart = index + 1
            require(bytes.isNotEmpty()) { "Atto fixture JSONL must not contain blank lines" }
            val text = bytes.decodeUtf8Strict()
            require(Json.parseToJsonElement(text) is JsonObject) { "Atto fixture JSONL values must be objects" }
            val transaction = AttoTransaction.fromJson(text)
            transactions += EncodedAttoTransaction(bytes, transaction)
        }
        return transactions
    }

    private fun writeZip(
        path: Path,
        transactions: Collection<AttoTransaction>,
    ) {
        path.parent?.let(Files::createDirectories)
        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW).use { output ->
            ZipOutputStream(output).use { zip ->
                val entry = ZipEntry(path.fileName.toString().removeSuffix(".zip") + ".jsonl")
                entry.time = 0L
                zip.putNextEntry(entry)
                for (transaction in transactions) {
                    zip.write(transaction.toJson().encodeToByteArray())
                    zip.write('\n'.code)
                }
                zip.closeEntry()
            }
        }
    }

    private fun writeValidation(
        path: Path,
        validation: AttoFixtureValidation,
    ) {
        Files.write(
            path,
            validation.encodedValidation(),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }
}

private data class EncodedAttoTransaction(
    val bytes: ByteArray,
    val transaction: AttoTransaction,
) {
    fun toItem(
        lane: String,
        sequence: Int,
    ): BenchmarkItem<AttoPublication> {
        val account = transaction.block.publicKey.toString()
        return BenchmarkItem(
            lane = lane,
            sequence = sequence,
            account = account,
            hash = transaction.hash.toString(),
            payload = AttoPublication(bytes),
        )
    }
}

private fun AttoFixtureSpec.provenance(): AttoGeneratorProvenance =
    AttoGeneratorProvenance(
        implementation = "Kotlin/JVM deterministic Atto fixture generator",
        commonsVersion = ATTO_COMMONS_VERSION,
        accountCount = accountCount,
        transactionCount = transactionCount,
        seed = seed,
        baseTimestamp = baseTimestamp,
        workSearchParallelism = 1,
    )

private fun Path.validation(transactionCount: Int): AttoFixtureFileValidation =
    AttoFixtureFileValidation(
        path = fileName.toString(),
        sha256 = if (Files.isRegularFile(this)) sha256() else "",
        transactionCount = transactionCount,
    )

private fun Path.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(this).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}

private fun MutableList<String>.requireThat(
    condition: Boolean,
    message: String,
) {
    if (!condition && size < MAX_REPORTED_ERRORS) add(message)
}

private fun validateLedgerOrder(
    initial: List<EncodedAttoTransaction>,
    benchmark: List<EncodedAttoTransaction>,
    errors: MutableList<String>,
) {
    val heads = mutableMapOf<String, AttoTransaction>()
    for (encoded in initial + benchmark) {
        val transaction = encoded.transaction
        val block = transaction.block
        val account = block.publicKey.toString()
        val previous = heads[account]
        if (previous == null) {
            errors.requireThat(
                block is AttoOpenBlock,
                "Account $account starts with ${block.type}; expected OPEN",
            )
        } else {
            errors.requireThat(
                block is PreviousSupport && block.previous == previous.hash,
                "Transaction ${transaction.hash} does not follow ${previous.hash}",
            )
            errors.requireThat(
                block.height == previous.height.next(),
                "Transaction ${transaction.hash} height ${block.height} does not follow ${previous.height}",
            )
            errors.requireThat(
                block.timestamp > previous.block.timestamp,
                "Transaction ${transaction.hash} timestamp does not increase within account $account",
            )
        }
        heads[account] = transaction
    }
}

private fun validateBenchmarkTimestamps(
    spec: AttoFixtureSpec,
    lanes: Iterable<List<EncodedAttoTransaction>>,
    errors: MutableList<String>,
) {
    val firstBenchmarkTimestamp = Instant.parse(spec.baseTimestamp) + (spec.accountCount * 2 + 1).milliseconds
    lanes.forEach { lane ->
        lane.forEachIndexed { sequence, encoded ->
            val expectedTimestamp = (firstBenchmarkTimestamp + sequence.milliseconds).toAtto()
            errors.requireThat(
                encoded.transaction.block.timestamp == expectedTimestamp,
                "Transaction ${encoded.transaction.hash} timestamp does not match shared round $sequence",
            )
        }
    }
}

private const val ATTO_COMMONS_VERSION = "7.0.2"
private const val GENESIS_PUBLIC_KEY = "3B6A27BCCEB6A42D62A3A8D02A6F0D73653215771DE243A63AC048A18B59DA29"
private const val MAX_REPORTED_ERRORS = 20

@OptIn(ExperimentalSerializationApi::class)
private val verificationJson =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

private fun AttoFixtureValidation.encodedValidation(): ByteArray = (verificationJson.encodeToString(this) + "\n").encodeToByteArray()
