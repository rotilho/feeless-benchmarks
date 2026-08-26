package dev.feeless.benchmarks.app

internal data class SuiteScenario(
    val implementation: Implementation,
    val fixture: String,
    val outputName: String,
    val expectedCount: Int,
)

internal object CanonicalScenarios {
    private val definitions =
        listOf(
            SuiteScenario(Implementation.NANO, "nano-serial", "nano-serial", SERIAL_SAMPLE_COUNT),
            SuiteScenario(Implementation.NANO, "nano-500", "nano-500", PARALLEL_SAMPLE_COUNT),
            SuiteScenario(Implementation.RSNANO, "nano-serial", "rsnano-serial", SERIAL_SAMPLE_COUNT),
            SuiteScenario(Implementation.RSNANO, "nano-500", "rsnano-500", PARALLEL_SAMPLE_COUNT),
            SuiteScenario(Implementation.ATTO, "atto-serial", "atto-serial", SERIAL_SAMPLE_COUNT),
            SuiteScenario(Implementation.ATTO, "atto-500", "atto-500", PARALLEL_SAMPLE_COUNT),
        )

    fun select(implementations: List<Implementation>): List<SuiteScenario> =
        implementations.flatMap { implementation -> definitions.filter { it.implementation == implementation } }

    fun outputName(
        implementation: Implementation,
        fixture: String,
    ): String? =
        definitions
            .singleOrNull { scenario ->
                scenario.implementation == implementation && scenario.fixture == fixture
            }?.outputName

    fun independentAccounts(
        implementation: Implementation,
        accountCount: Int,
    ): SuiteScenario {
        require(accountCount > 0) { "account count must be positive" }
        val fixturePrefix = if (implementation == Implementation.ATTO) "atto" else "nano"
        return SuiteScenario(
            implementation = implementation,
            fixture = "$fixturePrefix-$accountCount",
            outputName = "${implementation.cliName}-$accountCount",
            expectedCount = Math.multiplyExact(accountCount, ITEMS_PER_ACCOUNT),
        )
    }

    private const val ITEMS_PER_ACCOUNT = 100
    private const val SERIAL_SAMPLE_COUNT = 1_000
    private const val PARALLEL_SAMPLE_COUNT = 500 * ITEMS_PER_ACCOUNT
}

internal object SuitePlan {
    fun scenarios(implementations: List<Implementation>): List<SuiteScenario> = CanonicalScenarios.select(implementations)
}
