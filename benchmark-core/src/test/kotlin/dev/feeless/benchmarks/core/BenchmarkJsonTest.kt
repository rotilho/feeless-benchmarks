package dev.feeless.benchmarks.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BenchmarkJsonTest {
    @Test
    fun `summary JSON preserves the established schema without manifest fields`() {
        // Given
        val summary =
            BenchmarkStatistics.summarize(
                listOf(
                    sample(start = 10, completion = 30, latency = 20, error = null),
                    sample(start = 11, completion = null, latency = null, error = "TimeoutException: late"),
                ),
            )

        // When
        val encoded = encodeSummaryJson(summary)
        val fields = Json.parseToJsonElement(encoded).jsonObject

        // Then
        assertEquals(
            setOf(
                "average_tps",
                "elapsed_ns",
                "error_count",
                "latency_ns",
                "peak_tps",
                "sample_count",
                "success_count",
            ),
            fields.keys,
        )
        assertFalse("manifest" in fields)
        assertFalse("provenance" in fields)
        assertEquals(summary, decodeSummaryJson(encoded))
        assertTrue(encoded.endsWith('\n'))
    }

    @Test
    fun `empty summary encodes and decodes explicit nulls`() {
        // Given
        val summary = BenchmarkStatistics.summarize(emptyList())

        // When
        val decoded = decodeSummaryJson(encodeSummaryJson(summary))

        // Then
        assertEquals(0, decoded.sampleCount)
        assertEquals(0, decoded.successCount)
        assertEquals(0, decoded.errorCount)
        assertNull(decoded.elapsedNs)
        assertNull(decoded.averageTps)
        assertNull(decoded.latencyNs.average)
        assertNull(decoded.latencyNs.min)
        assertNull(decoded.latencyNs.max)
        assertEquals(0, decoded.peakTps)
    }

    private fun sample(
        start: Long,
        completion: Long?,
        latency: Long?,
        error: String?,
    ): BenchmarkSample =
        BenchmarkSample(
            implementation = "node",
            fixture = "fixture",
            lane = "a",
            sequence = 1,
            account = "account",
            hash = "hash",
            startMonotonicNs = start,
            completionMonotonicNs = completion,
            latencyNs = latency,
            error = error,
        )
}
