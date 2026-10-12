package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSummary

internal object RunAcceptance {
    fun requireClean(
        scenario: String,
        expectedCount: Int,
        summary: BenchmarkSummary,
        canonicalExpectedCount: Int? = null,
    ) {
        check(
            summary.sampleCount == expectedCount &&
                summary.successCount == expectedCount &&
                summary.errorCount == 0,
        ) {
            "scenario $scenario did not complete cleanly: " +
                "expected=$expectedCount, samples=${summary.sampleCount}, " +
                "successes=${summary.successCount}, errors=${summary.errorCount}"
        }
        if (canonicalExpectedCount != null) {
            check(expectedCount == canonicalExpectedCount) {
                "suite scenario $scenario has $expectedCount measured items; expected $canonicalExpectedCount"
            }
        }
    }
}
