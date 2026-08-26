package dev.feeless.benchmarks.core

/** A complete benchmark workload. Setup is published before any measured lane starts. */
data class BenchmarkScenario<P>(
    val implementation: String,
    val fixture: String,
    val setup: List<BenchmarkItem<P>>,
    val lanes: Map<String, List<BenchmarkItem<P>>>,
    val expectedCount: Int,
)
