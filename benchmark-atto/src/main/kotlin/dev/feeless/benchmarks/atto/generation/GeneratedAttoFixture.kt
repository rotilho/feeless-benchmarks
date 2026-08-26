package dev.feeless.benchmarks.atto.generation

import cash.atto.commons.AttoTransaction

internal data class GeneratedAttoFixture(
    val initialTransactions: List<AttoTransaction>,
    val benchmarkTransactions: List<AttoTransaction>,
)
