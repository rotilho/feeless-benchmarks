package dev.feeless.benchmarks.atto

import kotlinx.serialization.Serializable

@Serializable
data class AttoFixtureSpec(
    val fixture: String,
    val accountCount: Int,
    val transactionCount: Int,
    val seed: Long,
    val baseTimestamp: String,
) {
    init {
        require(fixture.matches(FIXTURE_NAME)) { "Fixture name must contain lowercase letters, digits, and hyphens" }
        require(accountCount > 0) { "Account count must be positive" }
        require(transactionCount > 0) { "Transaction count must be positive" }
        require(transactionCount % accountCount == 0) {
            "Transaction count must be divisible by account count"
        }
    }

    val initialFileName: String
        get() = "$fixture-initial.zip"

    val benchmarkFileName: String
        get() = "$fixture-benchmark.zip"

    val verificationFileName: String
        get() = "$fixture-verification.json"
}

private val FIXTURE_NAME = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
