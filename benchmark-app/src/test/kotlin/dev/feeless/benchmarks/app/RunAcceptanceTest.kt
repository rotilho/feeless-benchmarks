package dev.feeless.benchmarks.app

import dev.feeless.benchmarks.core.BenchmarkSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunAcceptanceTest {
    @Test
    fun `rejects failures and incomplete samples`() {
        val failure = sample(sequence = 1, error = "rejected", completed = false)
        val success = sample(sequence = 2)

        assertFailsWith<IllegalStateException> {
            RunAcceptance.requireClean("fixture", 2, listOf(failure, success))
        }
        assertFailsWith<IllegalStateException> {
            RunAcceptance.requireClean("fixture", 2, listOf(success))
        }
    }

    @Test
    fun `canonical suite requires its declared sample count`() {
        val samples = List(2) { sequence -> sample(sequence) }

        assertFailsWith<IllegalStateException> {
            RunAcceptance.requireClean("fixture", 2, samples, canonicalExpectedCount = 1_000)
        }
        assertEquals(2, RunAcceptance.requireClean("fixture", 2, samples).successCount)
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
