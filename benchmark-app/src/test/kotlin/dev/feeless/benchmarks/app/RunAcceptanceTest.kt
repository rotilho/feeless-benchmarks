package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import dev.feeless.benchmarks.core.BenchmarkStatistics
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RunAcceptanceTest {
    @Test
    fun `rejects failures and incomplete samples`() {
        // Given
        val failure = sample(sequence = 1, error = "rejected", completed = false)
        val success = sample(sequence = 2)
        val failedSummary = BenchmarkStatistics.summarize(listOf(failure, success))
        val incompleteSummary = BenchmarkStatistics.summarize(listOf(success))

        // When
        val rejectFailedRun = {
            RunAcceptance.requireClean("fixture", 2, failedSummary)
        }
        val rejectIncompleteRun = {
            RunAcceptance.requireClean("fixture", 2, incompleteSummary)
        }

        // Then
        assertFailsWith<IllegalStateException> { rejectFailedRun() }
        assertFailsWith<IllegalStateException> { rejectIncompleteRun() }
    }

    @Test
    fun `canonical suite requires its declared sample count`() {
        // Given
        val samples = List(2) { sequence -> sample(sequence) }
        val summary = BenchmarkStatistics.summarize(samples)

        // When
        val rejectWrongCanonicalCount = {
            RunAcceptance.requireClean("fixture", 2, summary, canonicalExpectedCount = 1_000)
        }
        val acceptCleanRun = { RunAcceptance.requireClean("fixture", 2, summary) }

        // Then
        assertFailsWith<IllegalStateException> { rejectWrongCanonicalCount() }
        acceptCleanRun()
    }

    private fun sample(
        sequence: Int,
        error: String? = null,
        completed: Boolean = true,
    ): BenchmarkSample =
        BenchmarkSample(
            implementation = "test",
            fixture = "fixture",
            lane = "lane",
            sequence = sequence,
            account = "account",
            hash = "hash-$sequence",
            startMonotonicNs = sequence.toLong(),
            completionMonotonicNs = if (completed) sequence + 1L else null,
            latencyNs = if (completed) 1L else null,
            error = error,
        )
}
