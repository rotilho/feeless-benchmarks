package dev.feeless.benchmarks.app

import kotlin.test.Test
import kotlin.test.assertEquals

class SuitePlanTest {
    @Test
    fun `default ordering completes Nano and RSNano before Atto`() {
        // Given
        val implementations = listOf(Implementation.NANO, Implementation.RSNANO, Implementation.ATTO)

        // When
        val scenarios = SuitePlan.scenarios(implementations)

        // Then
        assertEquals(
            listOf(
                SuiteScenario(Implementation.NANO, "nano-serial", "nano-serial", 1_000),
                SuiteScenario(Implementation.NANO, "nano-500", "nano-500", 50_000),
                SuiteScenario(Implementation.RSNANO, "nano-serial", "rsnano-serial", 1_000),
                SuiteScenario(Implementation.RSNANO, "nano-500", "rsnano-500", 50_000),
                SuiteScenario(Implementation.ATTO, "atto-serial", "atto-serial", 1_000),
                SuiteScenario(Implementation.ATTO, "atto-500", "atto-500", 50_000),
            ),
            scenarios,
        )
    }

    @Test
    fun `RSNano reuses Nano fixtures while keeping distinct output names`() {
        // Given
        val implementation = listOf(Implementation.RSNANO)

        // When
        val scenarios = SuitePlan.scenarios(implementation)

        // Then
        assertEquals(listOf("nano-serial", "nano-500"), scenarios.map(SuiteScenario::fixture))
        assertEquals(listOf("rsnano-serial", "rsnano-500"), scenarios.map(SuiteScenario::outputName))
        assertEquals(listOf(1_000, 50_000), scenarios.map(SuiteScenario::expectedCount))
    }

    @Test
    fun `independent-account scenarios derive names and counts from the account count`() {
        // Given
        val accountCounts = listOf(100, 500)

        // When
        val nano = accountCounts.map { accountCount -> CanonicalScenarios.independentAccounts(Implementation.NANO, accountCount) }
        val atto = accountCounts.map { accountCount -> CanonicalScenarios.independentAccounts(Implementation.ATTO, accountCount) }

        // Then
        assertEquals(listOf("nano-100", "nano-500"), nano.map(SuiteScenario::fixture))
        assertEquals(listOf("atto-100", "atto-500"), atto.map(SuiteScenario::fixture))
        assertEquals(listOf(10_000, 50_000), nano.map(SuiteScenario::expectedCount))
    }
}
