package dev.feeless.benchmarks.core

/** One fully prepared publication in an account lane. */
data class BenchmarkItem<P>(
    val lane: String,
    val sequence: Int,
    val account: String,
    val hash: String,
    val payload: P,
)
