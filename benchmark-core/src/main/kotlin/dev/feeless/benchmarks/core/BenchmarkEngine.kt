package dev.feeless.benchmarks.core

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CancellationException
import kotlin.time.Duration

/** Owns the complete measured boundary, including every monotonic-clock read. */
class BenchmarkEngine(
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun <P> run(
        scenario: BenchmarkScenario<P>,
        adapter: PublishAdapter<P>,
        timeout: Duration,
    ): List<BenchmarkSample> {
        publishSetup(scenario.setup, adapter, timeout)

        return coroutineScope {
            scenario.lanes.values
                .map { lane ->
                    async {
                        runLane(
                            implementation = scenario.implementation,
                            fixture = scenario.fixture,
                            items = lane,
                            adapter = adapter,
                            timeout = timeout,
                        )
                    }
                }.awaitAll()
                .flatten()
        }
    }

    private suspend fun <P> publishSetup(
        items: List<BenchmarkItem<P>>,
        adapter: PublishAdapter<P>,
        timeout: Duration,
    ) {
        items.forEach { item -> adapter.publish(item, timeout) }
    }

    private suspend fun <P> runLane(
        implementation: String,
        fixture: String,
        items: List<BenchmarkItem<P>>,
        adapter: PublishAdapter<P>,
        timeout: Duration,
    ): List<BenchmarkSample> {
        val samples = mutableListOf<BenchmarkSample>()
        for (item in items) {
            val sample = measure(implementation, fixture, item, adapter, timeout)
            samples += sample
            if (sample.error != null) {
                break
            }
        }
        return samples
    }

    private suspend fun <P> measure(
        implementation: String,
        fixture: String,
        item: BenchmarkItem<P>,
        adapter: PublishAdapter<P>,
        timeout: Duration,
    ): BenchmarkSample {
        val startNs = nanoTime()
        try {
            adapter.publish(item, timeout)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return BenchmarkSample(
                implementation = implementation,
                fixture = fixture,
                lane = item.lane,
                sequence = item.sequence,
                account = item.account,
                hash = item.hash,
                startMonotonicNs = startNs,
                completionMonotonicNs = null,
                latencyNs = null,
                error = error.asSampleError(),
            )
        }

        val completionNs = nanoTime()
        return BenchmarkSample(
            implementation = implementation,
            fixture = fixture,
            lane = item.lane,
            sequence = item.sequence,
            account = item.account,
            hash = item.hash,
            startMonotonicNs = startNs,
            completionMonotonicNs = completionNs,
            latencyNs = completionNs - startNs,
            error = null,
        )
    }
}

private fun Exception.asSampleError(): String {
    val type = javaClass.simpleName.ifEmpty { javaClass.name }
    val detail = message
    return if (detail.isNullOrEmpty()) type else "$type: $detail"
}
