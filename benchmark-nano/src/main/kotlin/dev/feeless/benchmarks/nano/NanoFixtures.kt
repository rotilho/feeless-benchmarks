package dev.feeless.benchmarks.nano

import dev.feeless.benchmarks.core.BenchmarkItem
import dev.feeless.benchmarks.core.BenchmarkScenario
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

@OptIn(ExperimentalSerializationApi::class)
object NanoFixtures {
    const val SCHEMA = "nano-v28.2-benchmark-fixture/v2"
    const val DEV_GENESIS_PRIVATE_KEY = "34F0A37AAD20F4A260F0A5B3CB3D7FB50673212263E58A380BC10474BB039CE4"
    const val DEV_GENESIS_PUBLIC_KEY = "B0311EA55708D6A53C75CDBF88300259C6D018522FE3D4D0A242E431F9E8B6D0"
    const val DEV_GENESIS_ACCOUNT = "nano_3e3j5tkog48pnny9dmfzj1r16pg8t1e76dz5tmac6iq689wyjfpiij4txtdo"
    const val DEV_GENESIS_HASH = "04270D7F11C4B2B472F2854C5A59F2A7E84226CE9ED799DE75744BD7D85FC9D9"
    const val NANO_IMAGE = "nanocurrency/nano:V28.2"
    const val RSNANO_IMAGE = "rsnano/rsnano:V3.1"
    private const val DEV_GENESIS_BALANCE = "340282366920938463463374607431768211455"
    private const val ACCOUNT_INITIAL_BALANCE = 1_000_000L
    private const val SEND_AMOUNT = 1L
    private const val NANO_BUILD_COMMIT = "0d8eea4"
    private const val ZERO = "0000000000000000000000000000000000000000000000000000000000000000"

    val canonicalSpecs: List<NanoFixtureSpec> =
        listOf(
            NanoFixtureSpec(fixture = "nano-serial", sourceCount = 1, blocksPerSource = 1_000),
            NanoFixtureSpec(fixture = "nano-500", sourceCount = 500, blocksPerSource = 100),
        )

    private val prettyJson =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }
    private val canonicalJson =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

    fun generateCanonical(targetDir: Path): List<NanoFixtureGeneration> {
        val paths = canonicalSpecs.map { spec -> targetDir.resolve(spec.fixtureFileName) }
        paths.flatMap { listOf(it, verificationPath(it)) }.forEach { path ->
            require(Files.notExists(path)) { "refusing to overwrite $path" }
        }
        Files.createDirectories(targetDir)
        return canonicalSpecs.zip(paths).map { (spec, path) ->
            generate(spec.fixture, spec.sourceCount, spec.blocksPerSource, path)
        }
    }

    fun generate(
        name: String,
        sourceCount: Int,
        blocksPerSource: Int,
        path: Path,
    ): NanoFixtureGeneration {
        require(name.isNotBlank()) { "fixture name must not be blank" }
        require(sourceCount > 0) { "sourceCount must be positive" }
        require(blocksPerSource > 0) { "blocksPerSource must be positive" }
        require(Files.notExists(path)) { "refusing to overwrite $path" }
        val verificationPath = verificationPath(path)
        require(Files.notExists(verificationPath)) { "refusing to overwrite $verificationPath" }

        val fixture = generateFixture(name, sourceCount, blocksPerSource)
        val fixtureBytes = (prettyJson.encodeToString(fixture) + "\n").encodeToByteArray()
        val validation = validate(fixture, sha256(fixtureBytes))
        check(validation.valid) { "generated fixture is invalid:\n${validation.errors.joinToString("\n")}" }
        val verificationBytes = (prettyJson.encodeToString(validation) + "\n").encodeToByteArray()

        promoteAtomically(path, fixtureBytes)
        promoteAtomically(verificationPath, verificationBytes)
        return NanoFixtureGeneration(path, verificationPath, validation)
    }

    fun validate(path: Path): NanoFixtureValidation {
        val bytes =
            runCatching { Files.readAllBytes(path) }.getOrElse { error ->
                return NanoFixtureValidation(valid = false, errors = listOf("cannot read $path: ${error.message}"))
            }
        val fixture =
            runCatching { prettyJson.decodeFromString<NanoFixture>(bytes.decodeToString()) }.getOrElse { error ->
                return NanoFixtureValidation(
                    valid = false,
                    errors = listOf("invalid fixture JSON: ${error.message}"),
                    fixtureSha256 = sha256(bytes),
                )
            }
        return validate(fixture, sha256(bytes))
    }

    fun validateWithVerification(path: Path): NanoFixtureValidation {
        val validation = validate(path)
        val verification = verificationPath(path)
        val verificationError =
            when {
                !Files.isRegularFile(verification) ->
                    "${verification.fileName}: verification artifact is missing"

                !Files.readAllBytes(verification).contentEquals(encodeValidation(validation).encodeToByteArray()) ->
                    "${verification.fileName}: verification artifact does not match the fixture"

                else -> null
            }
        return if (verificationError == null) {
            validation
        } else {
            validation.copy(valid = false, errors = validation.errors + verificationError)
        }
    }

    fun validateWithVerification(
        spec: NanoFixtureSpec,
        path: Path,
    ): NanoFixtureValidation {
        val validation = validateWithVerification(path)
        val shapeErrors = mutableListOf<String>()
        val expectedMeasuredCount = Math.multiplyExact(spec.sourceCount, spec.blocksPerSource)
        val expectedSetupCount = Math.multiplyExact(spec.sourceCount - 1, 2)
        if (validation.fixture != spec.fixture) {
            shapeErrors += "fixture name is ${validation.fixture}; expected ${spec.fixture}"
        }
        if (validation.sourceCount != spec.sourceCount) {
            shapeErrors += "fixture has ${validation.sourceCount} sources; expected ${spec.sourceCount}"
        }
        if (validation.setupCount != expectedSetupCount) {
            shapeErrors += "fixture has ${validation.setupCount} setup blocks; expected $expectedSetupCount"
        }
        if (validation.measuredCount != expectedMeasuredCount) {
            shapeErrors += "fixture has ${validation.measuredCount} measured blocks; expected $expectedMeasuredCount"
        }
        val expectedDistribution = mapOf(spec.blocksPerSource.toString() to spec.sourceCount)
        if (validation.laneDistribution != expectedDistribution) {
            shapeErrors += "fixture lane distribution is ${validation.laneDistribution}; expected $expectedDistribution"
        }
        return validation.copy(
            valid = validation.valid && shapeErrors.isEmpty(),
            errors = validation.errors + shapeErrors,
        )
    }

    fun encodeValidation(validation: NanoFixtureValidation): String = prettyJson.encodeToString(validation) + "\n"

    fun loadScenario(
        path: Path,
        implementation: String,
    ): BenchmarkScenario<NanoPublication> {
        require(implementation == "nano" || implementation == "rsnano") {
            "Nano fixture implementation must be 'nano' or 'rsnano'"
        }
        val validation = validate(path)
        require(validation.valid) { "invalid Nano fixture $path:\n${validation.errors.joinToString("\n")}" }
        val fixture = prettyJson.decodeFromString<NanoFixture>(Files.readString(path))
        val setup =
            fixture.setupBlocks.mapIndexed { index, entry ->
                entry.toBenchmarkItem(lane = "setup", sequence = index)
            }
        val lanes = linkedMapOf<String, List<BenchmarkItem<NanoPublication>>>()
        fixture.measuredLanes.forEach { lane ->
            lanes[lane.account] =
                lane.blocks.mapIndexed { index, entry ->
                    entry.toBenchmarkItem(lane.account, entry.sequence ?: index)
                }
        }
        return BenchmarkScenario(
            implementation = implementation,
            fixture = fixture.name,
            setup = setup,
            lanes = lanes,
            expectedCount = fixture.expected.measuredCount,
        )
    }

    private fun generateFixture(
        name: String,
        sourceCount: Int,
        blocksPerSource: Int,
    ): NanoFixture {
        val genesisKey = DEV_GENESIS_PRIVATE_KEY.hexBytes(32)
        check(NanoEd25519.publicKey(genesisKey).upperHex() == DEV_GENESIS_PUBLIC_KEY)
        check(NanoAccounts.encode(DEV_GENESIS_PUBLIC_KEY.hexBytes(32)) == DEV_GENESIS_ACCOUNT)

        val sinkKey = deterministicPrivateKey("$name:sink")
        val sinkPublic = NanoEd25519.publicKey(sinkKey).upperHex()
        val sink =
            NanoSink(
                label = "$name:sink",
                account = NanoAccounts.encode(sinkPublic.hexBytes(32)),
                publicKey = sinkPublic,
                unopened = true,
            )

        val sourceKeys = linkedMapOf(DEV_GENESIS_ACCOUNT to genesisKey)
        val sourceAccounts =
            mutableListOf(
                NanoSourceAccount(
                    index = 0,
                    label = "dev-genesis",
                    account = DEV_GENESIS_ACCOUNT,
                    publicKey = DEV_GENESIS_PUBLIC_KEY,
                    initialBalance = DEV_GENESIS_BALANCE,
                ),
            )
        repeat(sourceCount - 1) { offset ->
            val index = offset + 1
            val privateKey = deterministicPrivateKey("$name:source:$index")
            val publicKey = NanoEd25519.publicKey(privateKey).upperHex()
            val account = NanoAccounts.encode(publicKey.hexBytes(32))
            sourceKeys[account] = privateKey
            sourceAccounts +=
                NanoSourceAccount(
                    index = index,
                    label = "source-${index.toString().padStart(3, '0')}",
                    account = account,
                    publicKey = publicKey,
                    initialBalance = ACCOUNT_INITIAL_BALANCE.toString(),
                )
        }

        val setup = mutableListOf<NanoFixtureEntry>()
        var genesisPrevious = DEV_GENESIS_HASH
        var genesisBalance = DEV_GENESIS_BALANCE.toBigInteger()
        val fundingEntries = mutableListOf<NanoFixtureEntry>()
        sourceAccounts.drop(1).forEach { source ->
            genesisBalance -= ACCOUNT_INITIAL_BALANCE.toBigInteger()
            val entry =
                createEntry(
                    privateKey = genesisKey,
                    previous = genesisPrevious,
                    balance = genesisBalance,
                    link = source.publicKey,
                    subtype = NanoBlockSubtype.SEND,
                    setupRole = "fund-source",
                )
            setup += entry
            fundingEntries += entry
            genesisPrevious = entry.hash
        }

        val openedByAccount = mutableMapOf<String, NanoFixtureEntry>()
        sourceAccounts.drop(1).zip(fundingEntries).forEach { (source, funding) ->
            val entry =
                createEntry(
                    privateKey = sourceKeys.getValue(source.account),
                    previous = ZERO,
                    balance = ACCOUNT_INITIAL_BALANCE.toBigInteger(),
                    link = funding.hash,
                    subtype = NanoBlockSubtype.RECEIVE,
                    setupRole = "open-source",
                )
            setup += entry
            openedByAccount[source.account] = entry
        }

        val laneStates =
            sourceAccounts.map { source ->
                MeasuredLaneState(
                    source = source,
                    previous = if (source.index == 0) genesisPrevious else openedByAccount.getValue(source.account).hash,
                    balance = if (source.index == 0) genesisBalance else ACCOUNT_INITIAL_BALANCE.toBigInteger(),
                )
            }
        val blocksByLane =
            generateRoundRobinLanes(laneStates, blocksPerSource) { state, sequence ->
                state.balance -= SEND_AMOUNT.toBigInteger()
                createEntry(
                    privateKey = sourceKeys.getValue(state.source.account),
                    previous = state.previous,
                    balance = state.balance,
                    link = sink.publicKey,
                    subtype = NanoBlockSubtype.SEND,
                    sequence = sequence,
                ).also { state.previous = it.hash }
            }
        val lanes =
            laneStates.zip(blocksByLane) { state, blocks ->
                NanoMeasuredLane(state.source.account, state.source.index, blocks)
            }

        val checksums = checksums(sourceAccounts, setup, lanes)
        return NanoFixture(
            schema = SCHEMA,
            name = name,
            network = "dev",
            generator =
                NanoGeneratorProvenance(
                    language = "Kotlin/JVM 21",
                    source = "nano/src/main/kotlin/dev/feeless/benchmarks/nano",
                    jnanoSource = "https://github.com/rotilho/jnano-commons",
                    jnanoRevision = "ce2bf78a321ee98764117de5dcc230a7466c2502",
                    license = "MIT",
                ),
            nano = NanoNodeProvenance(NANO_IMAGE, "V28.2", NANO_BUILD_COMMIT),
            thresholdProfile = NanoWork.v28DevProfile,
            cryptoTrustBoundary =
                "Account encoding, state hashes, Ed25519-Blake2b signatures, and deterministic work are generated offline by this Kotlin module from the pinned MIT JNano algorithms; Bouncy Castle supplies BLAKE2b and net.i2p supplies EdDSA arithmetic.",
            genesis =
                NanoGenesis(
                    DEV_GENESIS_ACCOUNT,
                    DEV_GENESIS_PUBLIC_KEY,
                    DEV_GENESIS_HASH,
                    DEV_GENESIS_BALANCE,
                ),
            sink = sink,
            sourceAccounts = sourceAccounts,
            setupBlocks = setup,
            measuredLanes = lanes,
            expected =
                NanoFixtureExpected(
                    sourceCount,
                    blocksPerSource,
                    sourceCount * blocksPerSource,
                    setup.size,
                ),
            checksums = checksums,
        )
    }

    private fun createEntry(
        privateKey: ByteArray,
        previous: String,
        balance: BigInteger,
        link: String,
        subtype: NanoBlockSubtype,
        sequence: Int? = null,
        setupRole: String? = null,
    ): NanoFixtureEntry {
        val account = NanoAccounts.encode(NanoEd25519.publicKey(privateKey))
        val root = NanoWork.root(account, previous)
        val work = NanoWork.deterministic(root, NanoLedgerEpoch.EPOCH_0, subtype)
        val block =
            NanoStateBlock.create(
                privateKey = privateKey,
                previous = previous,
                representative = DEV_GENESIS_ACCOUNT,
                balance = balance,
                link = link,
                work = work,
            )
        return NanoFixtureEntry(block.hash(), subtype, block, sequence, setupRole)
    }

    private fun validate(
        fixture: NanoFixture,
        fixtureSha256: String,
    ): NanoFixtureValidation {
        val errors = mutableListOf<String>()
        if (fixture.schema != SCHEMA) errors += "wrong fixture schema: ${fixture.schema}"
        if (fixture.network != "dev") errors += "fixture network must be dev"
        if (fixture.generator.jnanoRevision != "ce2bf78a321ee98764117de5dcc230a7466c2502") {
            errors += "unexpected JNano source revision"
        }
        if (fixture.thresholdProfile != NanoWork.v28DevProfile) errors += "unexpected threshold profile"
        if (fixture.genesis != NanoGenesis(DEV_GENESIS_ACCOUNT, DEV_GENESIS_PUBLIC_KEY, DEV_GENESIS_HASH, DEV_GENESIS_BALANCE)) {
            errors += "fixture genesis does not match Nano's canonical dev genesis"
        }

        val expectedChecksums = checksums(fixture.sourceAccounts, fixture.setupBlocks, fixture.measuredLanes)
        val checksumsValid = fixture.checksums == expectedChecksums
        if (!checksumsValid) errors += "fixture section SHA-256 checksums do not match"

        val expected = fixture.expected
        if (fixture.sourceAccounts.size != expected.sourceCount) errors += "source account count mismatch"
        if (fixture.measuredLanes.size != expected.sourceCount) errors += "measured lane count mismatch"
        if (fixture.setupBlocks.size != expected.setupCount) errors += "setup count mismatch"
        val measuredCount = fixture.measuredLanes.sumOf { it.blocks.size }
        if (measuredCount != expected.measuredCount) errors += "measured count mismatch"
        if (expected.measuredCount != expected.sourceCount * expected.blocksPerSource) {
            errors += "expected measured count is inconsistent"
        }
        val laneDistribution =
            fixture.measuredLanes
                .groupingBy { it.blocks.size.toString() }
                .eachCount()
                .toSortedMap()
        if (laneDistribution != mapOf(expected.blocksPerSource.toString() to expected.sourceCount)) {
            errors += "lane distribution mismatch: $laneDistribution"
        }

        fixture.sourceAccounts.forEach { source ->
            if (runCatching { NanoAccounts.encode(source.publicKey.hexBytes(32)) }.getOrNull() != source.account) {
                errors += "source account/public key mismatch: ${source.label}"
            }
        }
        if (runCatching { NanoAccounts.encode(fixture.sink.publicKey.hexBytes(32)) }.getOrNull() != fixture.sink.account) {
            errors += "sink account/public key mismatch"
        }
        if (!fixture.sink.unopened) errors += "sink is not marked unopened"

        val allEntries = fixture.setupBlocks + fixture.measuredLanes.flatMap { it.blocks }
        allEntries.forEach { entry -> validateEntry(entry, errors) }
        val hashes = allEntries.map { it.hash }
        if (hashes.toSet().size != hashes.size) errors += "predicted hashes are not unique"

        val heads = mutableMapOf(DEV_GENESIS_ACCOUNT to DEV_GENESIS_HASH)
        val balances = mutableMapOf(DEV_GENESIS_ACCOUNT to DEV_GENESIS_BALANCE.toBigInteger())
        fixture.setupBlocks.forEach { entry ->
            val block = entry.block
            when (entry.setupRole) {
                "fund-source" -> {
                    if (block.account != DEV_GENESIS_ACCOUNT) errors += "fund-source is not from dev genesis: ${entry.hash}"
                    if (block.previous != heads[block.account]) errors += "broken setup previous: ${entry.hash}"
                    val priorBalance = balances[block.account]
                    val blockBalance = block.balance.toBigIntegerOrNull()
                    if (priorBalance == null || blockBalance == null || blockBalance >= priorBalance) {
                        errors += "invalid setup balance: ${entry.hash}"
                    }
                    blockBalance?.let { balances[block.account] = it }
                }

                "open-source" -> {
                    if (block.previous != ZERO) errors += "open block previous is nonzero: ${entry.hash}"
                    if (block.balance != ACCOUNT_INITIAL_BALANCE.toString()) errors += "invalid open balance: ${entry.hash}"
                    balances[block.account] = block.balance.toBigIntegerOrNull() ?: BigInteger.valueOf(-1)
                }

                else -> errors += "unknown setup role ${entry.setupRole}: ${entry.hash}"
            }
            heads[block.account] = entry.hash
        }

        val sourceByAccount = fixture.sourceAccounts.associateBy { it.account }
        fixture.measuredLanes.forEach { lane ->
            val source = sourceByAccount[lane.account]
            if (source == null) errors += "lane account is not a source: ${lane.account}"
            if (source != null && source.index != lane.sourceIndex) errors += "lane source index mismatch: ${lane.account}"
            var previous = heads[lane.account]
            var balance = balances[lane.account]
            lane.blocks.forEachIndexed { sequence, entry ->
                val block = entry.block
                if (entry.sequence != sequence) errors += "bad measured sequence: ${entry.hash}"
                if (entry.subtype != NanoBlockSubtype.SEND) errors += "measured block is not send: ${entry.hash}"
                if (block.account != lane.account) errors += "lane block account mismatch: ${entry.hash}"
                if (block.previous != previous) errors += "broken measured previous: ${entry.hash}"
                if (block.link != fixture.sink.publicKey) errors += "measured block does not target sink: ${entry.hash}"
                val blockBalance = block.balance.toBigIntegerOrNull()
                if (balance == null || blockBalance != balance - SEND_AMOUNT.toBigInteger()) {
                    errors += "bad measured balance: ${entry.hash}"
                }
                balance = blockBalance
                previous = entry.hash
            }
        }
        if (fixture.measuredLanes.map { it.account }.toSet() != sourceByAccount.keys) {
            errors += "source/lane account sets differ"
        }
        if (fixture.sink.account in sourceByAccount) errors += "sink is also a source account"

        return NanoFixtureValidation(
            valid = errors.isEmpty(),
            errors = errors,
            fixture = fixture.name,
            fixtureSha256 = fixtureSha256,
            sourceCount = fixture.sourceAccounts.size,
            setupCount = fixture.setupBlocks.size,
            measuredCount = measuredCount,
            uniquePredictedHashes = hashes.toSet().size,
            laneDistribution = laneDistribution,
            signatureCount = allEntries.count { it.block.signature.isNotBlank() },
            workCount = allEntries.count { it.block.work.isNotBlank() },
            chainPreviousLinksValid = errors.none { "previous" in it },
            balancesValid = errors.none { "balance" in it },
            checksumsValid = checksumsValid,
            sinkUnopened = fixture.sink.unopened,
        )
    }

    private fun validateEntry(
        entry: NanoFixtureEntry,
        errors: MutableList<String>,
    ) {
        val computedHash =
            runCatching { entry.block.hash() }.getOrElse { error ->
                errors += "cannot hash ${entry.hash}: ${error.message}"
                return
            }
        if (entry.hash != computedHash) errors += "predicted hash mismatch: ${entry.hash}"
        if (!entry.block.hasValidSignature()) errors += "invalid signature: ${entry.hash}"
        if (entry.block.type != "state") errors += "non-state block: ${entry.hash}"
        if (runCatching { NanoAccounts.encode(entry.block.link.hexBytes(32)) }.getOrNull() != entry.block.linkAsAccount) {
            errors += "link_as_account mismatch: ${entry.hash}"
        }
        val root =
            runCatching { NanoWork.root(entry.block.account, entry.block.previous) }.getOrElse { error ->
                errors += "invalid work root ${entry.hash}: ${error.message}"
                return
            }
        if (!NanoWork.isValid(root, entry.block.work, NanoLedgerEpoch.EPOCH_0, entry.subtype)) {
            errors += "invalid work: ${entry.hash}"
        }
    }

    private fun checksums(
        sourceAccounts: List<NanoSourceAccount>,
        setup: List<NanoFixtureEntry>,
        lanes: List<NanoMeasuredLane>,
    ): NanoFixtureChecksums =
        NanoFixtureChecksums(
            sourceAccountsSha256 = sha256(canonicalJson.encodeToString(sourceAccounts).encodeToByteArray()),
            setupBlocksSha256 = sha256(canonicalJson.encodeToString(setup).encodeToByteArray()),
            measuredLanesSha256 = sha256(canonicalJson.encodeToString(lanes).encodeToByteArray()),
        )

    private fun deterministicPrivateKey(label: String): ByteArray = blake2b(32, "nano-v28.2-benchmark:$label".encodeToByteArray())

    private fun NanoFixtureEntry.toBenchmarkItem(
        lane: String,
        sequence: Int,
    ): BenchmarkItem<NanoPublication> =
        BenchmarkItem(
            lane = lane,
            sequence = sequence,
            account = block.account,
            hash = hash,
            payload = NanoPublication(subtype.name.lowercase(), block),
        )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .upperHex()
            .lowercase()

    private fun verificationPath(path: Path): Path =
        path.resolveSibling("${path.fileName.toString().removeSuffix(".json")}-verification.json")

    private fun promoteAtomically(
        path: Path,
        bytes: ByteArray,
    ) {
        val parent = path.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING)
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

internal fun <S, T> generateRoundRobinLanes(
    sources: List<S>,
    itemsPerSource: Int,
    generateItem: (source: S, sequence: Int) -> T,
): List<List<T>> {
    require(sources.isNotEmpty()) { "sources must not be empty" }
    require(itemsPerSource > 0) { "itemsPerSource must be positive" }

    val lanes = List(sources.size) { ArrayList<T>(itemsPerSource) }
    repeat(itemsPerSource) { sequence ->
        sources.forEachIndexed { laneIndex, source ->
            lanes[laneIndex] += generateItem(source, sequence)
        }
    }
    return lanes
}

private class MeasuredLaneState(
    val source: NanoSourceAccount,
    var previous: String,
    var balance: BigInteger,
)
