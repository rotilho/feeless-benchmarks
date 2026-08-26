package dev.feeless.benchmarks.core

import kotlin.time.Duration

fun interface PublishAdapter<P> {
    /** Returns only after the exact item's externally observable completion. */
    suspend fun publish(
        item: BenchmarkItem<P>,
        timeout: Duration,
    )
}
