package dev.feeless.benchmarks.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LatencySummary(
    val average: Double?,
    val count: Int,
    val max: Long?,
    val min: Long?,
    val p50: Long?,
    val p90: Long?,
    val p95: Long?,
    val p99: Long?,
    val sum: Long,
)

data class ThroughputSummary(
    val elapsedNs: Long?,
    val averageTps: Double?,
    val peakTps: Int,
)

@Serializable
data class BenchmarkSummary(
    @SerialName("average_tps")
    val averageTps: Double?,
    @SerialName("elapsed_ns")
    val elapsedNs: Long?,
    @SerialName("error_count")
    val errorCount: Int,
    @SerialName("latency_ns")
    val latencyNs: LatencySummary,
    @SerialName("peak_tps")
    val peakTps: Int,
    @SerialName("sample_count")
    val sampleCount: Int,
    @SerialName("success_count")
    val successCount: Int,
)
