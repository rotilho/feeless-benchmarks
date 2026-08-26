package dev.feeless.benchmarks.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BenchmarkEngineTest {
    @Test
    fun `success reads the clock exactly twice around publication`() =
        runBlocking {
            // Given
            val events = mutableListOf<String>()
            val readings = ArrayDeque(listOf(1_000L, 1_075L))
            var clockCalls = 0
            val engine =
                BenchmarkEngine {
                    clockCalls += 1
                    events += "clock-$clockCalls"
                    readings.removeFirst()
                }
            val calls = mutableListOf<Pair<String, Duration>>()
            val adapter =
                PublishAdapter<String> { published, timeout ->
                    events += "publish"
                    calls += published.payload to timeout
                }

            // When
            val samples = engine.run(scenario(item("a", 1)), adapter, 2.5.seconds)

            // Then
            assertEquals(listOf("clock-1", "publish", "clock-2"), events)
            assertEquals(2, clockCalls)
            assertEquals(listOf("payload-a-1" to 2.5.seconds), calls)
            assertEquals(
                BenchmarkSample(
                    implementation = "test-node",
                    fixture = "fixture-1",
                    lane = "a",
                    sequence = 1,
                    account = "account-a",
                    hash = "hash-a-1",
                    startMonotonicNs = 1_000,
                    completionMonotonicNs = 1_075,
                    latencyNs = 75,
                    error = null,
                ),
                samples.single(),
            )
        }

    @Test
    fun `setup is serial and does not read the clock`() =
        runBlocking {
            // Given
            val published = mutableListOf<String>()
            val engine = BenchmarkEngine { error("setup must not read the benchmark clock") }
            val workload =
                BenchmarkScenario(
                    implementation = "test-node",
                    fixture = "fixture-1",
                    setup = listOf(item("setup", 1), item("setup", 2)),
                    lanes = emptyMap(),
                    expectedCount = 0,
                )
            val adapter = PublishAdapter<String> { publishedItem, _ -> published += publishedItem.payload }

            // When
            val samples = engine.run(workload, adapter, 4.seconds)

            // Then
            assertEquals(listOf("payload-setup-1", "payload-setup-2"), published)
            assertTrue(samples.isEmpty())
        }

    @Test
    fun `lanes run concurrently while each lane remains serial`() =
        runBlocking {
            // Given
            val firstAStarted = CompletableDeferred<Unit>()
            val firstBStarted = CompletableDeferred<Unit>()
            val releaseFirstA = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            var tick = 0L
            val engine = BenchmarkEngine { ++tick }
            val adapter =
                PublishAdapter<String> { publishedItem, _ ->
                    val key = "${publishedItem.lane}${publishedItem.sequence}"
                    events += "start-$key"
                    when (key) {
                        "a1" -> {
                            firstAStarted.complete(Unit)
                            firstBStarted.await()
                            releaseFirstA.await()
                        }

                        "b1" -> {
                            firstBStarted.complete(Unit)
                            firstAStarted.await()
                            releaseFirstA.complete(Unit)
                        }
                    }
                    events += "end-$key"
                }
            val workload =
                scenario(
                    item("a", 1),
                    item("a", 2),
                    item("b", 1),
                    item("b", 2),
                )

            // When
            val samples = engine.run(workload, adapter, 1.seconds)

            // Then
            assertEquals(4, samples.size)
            assertTrue("start-a1" in events.take(2))
            assertTrue("start-b1" in events.take(2))
            assertTrue(events.indexOf("end-a1") < events.indexOf("start-a2"))
            assertTrue(events.indexOf("end-b1") < events.indexOf("start-b2"))
        }

    @Test
    fun `samples retain scenario lane order even when another lane completes first`() =
        runBlocking {
            // Given
            val firstAStarted = CompletableDeferred<Unit>()
            val laneBCompleted = CompletableDeferred<Unit>()
            var tick = 0L
            val engine = BenchmarkEngine { ++tick }
            val adapter =
                PublishAdapter<String> { publishedItem, _ ->
                    when (publishedItem.lane) {
                        "a" -> {
                            firstAStarted.complete(Unit)
                            laneBCompleted.await()
                        }

                        "b" -> {
                            firstAStarted.await()
                            laneBCompleted.complete(Unit)
                        }
                    }
                }

            // When
            val samples = engine.run(scenario(item("a", 1), item("b", 1)), adapter, 1.seconds)

            // Then
            assertEquals(listOf("a", "b"), samples.map(BenchmarkSample::lane))
        }

    @Test
    fun `failure records one clock reading and stops only its lane`() =
        runBlocking {
            // Given
            val published = mutableListOf<String>()
            var tick = 100L
            var clockCalls = 0
            val engine =
                BenchmarkEngine {
                    clockCalls += 1
                    ++tick
                }
            val adapter =
                PublishAdapter<String> { publishedItem, _ ->
                    val key = "${publishedItem.lane}${publishedItem.sequence}"
                    published += key
                    if (key == "a1") throw IllegalStateException("publish rejected")
                }

            // When
            val samples =
                engine.run(
                    scenario(item("a", 1), item("a", 2), item("b", 1), item("b", 2)),
                    adapter,
                    1.seconds,
                )

            // Then
            assertFalse("a2" in published)
            assertTrue("b1" in published)
            assertTrue("b2" in published)
            assertEquals(3, samples.size)
            assertEquals(5, clockCalls)
            val failed = samples.single { sample -> sample.lane == "a" }
            assertEquals("IllegalStateException: publish rejected", failed.error)
            assertNull(failed.completionMonotonicNs)
            assertNull(failed.latencyNs)
        }

    @Test
    fun `exception without a message records only its type`() =
        runBlocking {
            // Given
            val engine = BenchmarkEngine { 1L }
            val adapter = PublishAdapter<String> { _, _ -> throw UnsupportedOperationException() }

            // When
            val sample = engine.run(scenario(item("a", 1)), adapter, 1.seconds).single()

            // Then
            assertEquals("UnsupportedOperationException", sample.error)
        }

    @Test
    fun `coroutine cancellation propagates instead of becoming a sample error`() =
        runBlocking {
            // Given
            val engine = BenchmarkEngine { 1L }
            val adapter = PublishAdapter<String> { _, _ -> throw CancellationException("cancelled") }

            // When
            val cancellation =
                assertFailsWith<CancellationException> {
                    engine.run(scenario(item("a", 1)), adapter, 1.seconds)
                }

            // Then
            assertEquals("cancelled", cancellation.message)
        }

    @Test
    fun `setup failures propagate without creating measured samples`() =
        runBlocking {
            // Given
            var clockCalls = 0
            val engine =
                BenchmarkEngine {
                    clockCalls += 1
                    clockCalls.toLong()
                }
            val workload =
                BenchmarkScenario(
                    implementation = "test-node",
                    fixture = "fixture-1",
                    setup = listOf(item("setup", 1)),
                    lanes = linkedMapOf("a" to listOf(item("a", 1))),
                    expectedCount = 1,
                )
            val adapter = PublishAdapter<String> { _, _ -> throw IllegalArgumentException("bad setup") }

            // When
            val failure =
                assertFailsWith<IllegalArgumentException> {
                    engine.run(workload, adapter, 1.seconds)
                }

            // Then
            assertEquals("bad setup", failure.message)
            assertEquals(0, clockCalls)
        }

    private fun scenario(vararg items: BenchmarkItem<String>): BenchmarkScenario<String> {
        val lanes =
            items
                .groupByTo(linkedMapOf(), BenchmarkItem<String>::lane)
                .mapValues { (_, laneItems) -> laneItems.toList() }
        return BenchmarkScenario(
            implementation = "test-node",
            fixture = "fixture-1",
            setup = emptyList(),
            lanes = lanes,
            expectedCount = items.size,
        )
    }

    private fun item(
        lane: String,
        sequence: Int,
    ): BenchmarkItem<String> =
        BenchmarkItem(
            lane = lane,
            sequence = sequence,
            account = "account-$lane",
            hash = "hash-$lane-$sequence",
            payload = "payload-$lane-$sequence",
        )
}
