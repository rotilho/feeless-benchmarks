package dev.feeless.benchmarks.nano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NanoGeneratorProvenance(
    val language: String,
    val source: String,
    @SerialName("jnano_source")
    val jnanoSource: String,
    @SerialName("jnano_revision")
    val jnanoRevision: String,
    val license: String,
)

@Serializable
data class NanoNodeProvenance(
    val image: String,
    val version: String,
    @SerialName("build_commit")
    val buildCommit: String,
)

@Serializable
data class NanoGenesis(
    val account: String,
    @SerialName("public_key")
    val publicKey: String,
    val hash: String,
    val balance: String,
)

@Serializable
data class NanoSink(
    val label: String,
    val account: String,
    @SerialName("public_key")
    val publicKey: String,
    val unopened: Boolean,
)

@Serializable
data class NanoSourceAccount(
    val index: Int,
    val label: String,
    val account: String,
    @SerialName("public_key")
    val publicKey: String,
    @SerialName("initial_balance")
    val initialBalance: String,
)

@Serializable
data class NanoFixtureEntry(
    val hash: String,
    val subtype: NanoBlockSubtype,
    val block: NanoStateBlock,
    val sequence: Int? = null,
    @SerialName("setup_role")
    val setupRole: String? = null,
)

@Serializable
data class NanoMeasuredLane(
    val account: String,
    @SerialName("source_index")
    val sourceIndex: Int,
    val blocks: List<NanoFixtureEntry>,
)

@Serializable
data class NanoFixtureExpected(
    @SerialName("source_count")
    val sourceCount: Int,
    @SerialName("blocks_per_source")
    val blocksPerSource: Int,
    @SerialName("measured_count")
    val measuredCount: Int,
    @SerialName("setup_count")
    val setupCount: Int,
)

@Serializable
data class NanoFixtureChecksums(
    @SerialName("source_accounts_sha256")
    val sourceAccountsSha256: String,
    @SerialName("setup_blocks_sha256")
    val setupBlocksSha256: String,
    @SerialName("measured_lanes_sha256")
    val measuredLanesSha256: String,
)

@Serializable
data class NanoFixture(
    val schema: String,
    val name: String,
    val network: String,
    val generator: NanoGeneratorProvenance,
    val nano: NanoNodeProvenance,
    @SerialName("threshold_profile")
    val thresholdProfile: NanoThresholdProfile,
    @SerialName("crypto_trust_boundary")
    val cryptoTrustBoundary: String,
    val genesis: NanoGenesis,
    val sink: NanoSink,
    @SerialName("source_accounts")
    val sourceAccounts: List<NanoSourceAccount>,
    @SerialName("setup_blocks")
    val setupBlocks: List<NanoFixtureEntry>,
    @SerialName("measured_lanes")
    val measuredLanes: List<NanoMeasuredLane>,
    val expected: NanoFixtureExpected,
    val checksums: NanoFixtureChecksums,
)

@Serializable
data class NanoPublication(
    val subtype: String,
    val block: NanoStateBlock,
)

@Serializable
data class NanoFixtureValidation(
    val valid: Boolean,
    val errors: List<String>,
    val fixture: String? = null,
    @SerialName("fixture_sha256")
    val fixtureSha256: String? = null,
    @SerialName("source_count")
    val sourceCount: Int = 0,
    @SerialName("setup_count")
    val setupCount: Int = 0,
    @SerialName("measured_count")
    val measuredCount: Int = 0,
    @SerialName("unique_predicted_hashes")
    val uniquePredictedHashes: Int = 0,
    @SerialName("lane_distribution")
    val laneDistribution: Map<String, Int> = emptyMap(),
    @SerialName("signature_count")
    val signatureCount: Int = 0,
    @SerialName("work_count")
    val workCount: Int = 0,
    @SerialName("chain_previous_links_valid")
    val chainPreviousLinksValid: Boolean = false,
    @SerialName("balances_valid")
    val balancesValid: Boolean = false,
    @SerialName("checksums_valid")
    val checksumsValid: Boolean = false,
    @SerialName("sink_unopened")
    val sinkUnopened: Boolean = false,
)

data class NanoFixtureGeneration(
    val fixturePath: java.nio.file.Path,
    val verificationPath: java.nio.file.Path,
    val validation: NanoFixtureValidation,
)
