package dev.feeless.benchmarks.atto

import kotlinx.serialization.Serializable

@Serializable
data class AttoFixtureValidation(
    val fixture: String,
    val generator: AttoGeneratorProvenance,
    val initial: AttoFixtureFileValidation,
    val benchmark: AttoFixtureFileValidation,
    val setupCount: Int,
    val laneCount: Int,
    val distinctTransactionsPerLane: List<Int>,
    val uniqueBenchmarkHashes: Int,
    val validTransactionCount: Int,
    val expectedTransactionCount: Int,
    val errors: List<String>,
    val valid: Boolean,
)

@Serializable
data class AttoGeneratorProvenance(
    val implementation: String,
    val commonsVersion: String,
    val accountCount: Int,
    val transactionCount: Int,
    val seed: Long,
    val baseTimestamp: String,
    val workSearchParallelism: Int,
)

@Serializable
data class AttoFixtureFileValidation(
    val path: String,
    val sha256: String,
    val transactionCount: Int,
)
