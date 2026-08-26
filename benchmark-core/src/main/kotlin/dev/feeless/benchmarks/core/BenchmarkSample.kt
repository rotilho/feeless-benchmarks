package dev.feeless.benchmarks.core

data class BenchmarkSample(
    val implementation: String,
    val fixture: String,
    val lane: String,
    val sequence: Int,
    val account: String,
    val hash: String,
    val startMonotonicNs: Long,
    val completionMonotonicNs: Long?,
    val latencyNs: Long?,
    val error: String?,
)
