package dev.feeless.benchmarks.nano

import dev.feeless.benchmarks.core.BenchmarkItem
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NanoPostCementAdapterTest {
    @Test
    fun `predicted hash is registered before process and exact post-cement event completes publication`() =
        runTest {
            // Given
            val registry = NanoConfirmationRegistry()
            val item = publicationItem()
            val rpc =
                FakeRpc { action, parameters ->
                    assertEquals("process", action)
                    assertEquals(
                        "state",
                        parameters
                            .getValue("block")
                            .jsonObject
                            .getValue("type")
                            .jsonPrimitive.content,
                    )
                    registry.dispatch(NanoConfirmationEvent(item.hash, "active_quorum"))
                    JsonObject(mapOf("hash" to JsonPrimitive(item.hash)))
                }
            val adapter = NanoPostCementAdapter(rpc, registry)

            // When
            adapter.publish(item, 1.seconds)

            // Then
            assertEquals(1, rpc.calls)
        }

    @Test
    fun `process must return the predicted hash`() =
        runTest {
            // Given
            val registry = NanoConfirmationRegistry()
            val adapter =
                NanoPostCementAdapter(
                    FakeRpc { _, _ -> JsonObject(mapOf("hash" to JsonPrimitive("WRONG"))) },
                    registry,
                )

            // When
            val error = assertFailsWith<NanoAdapterException> { adapter.publish(publicationItem(), 1.seconds) }

            // Then
            assertTrue("process hash mismatch" in error.message.orEmpty())
        }

    @Test
    fun `confirmation type must represent a post-cement event`() =
        runTest {
            // Given
            val registry = NanoConfirmationRegistry()
            val item = publicationItem()
            val adapter =
                NanoPostCementAdapter(
                    FakeRpc { _, _ ->
                        registry.dispatch(NanoConfirmationEvent(item.hash, "active_started"))
                        JsonObject(mapOf("hash" to JsonPrimitive(item.hash)))
                    },
                    registry,
                )

            // When
            val error = assertFailsWith<NanoAdapterException> { adapter.publish(item, 1.seconds) }

            // Then
            assertTrue("confirmation_type" in error.message.orEmpty())
        }

    @Test
    fun `one timeout covers process and exact confirmation wait`() =
        runTest {
            // Given
            val adapter =
                NanoPostCementAdapter(
                    FakeRpc { _, _ -> JsonObject(mapOf("hash" to JsonPrimitive(publicationItem().hash))) },
                    NanoConfirmationRegistry(),
                )

            // When / Then
            val error =
                assertFailsWith<NanoAdapterException> {
                    adapter.publish(publicationItem(), 10.milliseconds)
                }
            assertTrue("timed out" in error.message.orEmpty())
        }

    @Test
    fun `caller cancellation propagates and removes pending registration`() =
        runTest {
            // Given
            val registry = NanoConfirmationRegistry()
            val item = publicationItem()
            val rpc = FakeRpc { _, _ -> JsonObject(mapOf("hash" to JsonPrimitive(item.hash))) }
            val adapter = NanoPostCementAdapter(rpc, registry)
            val publication = launch { adapter.publish(item, 10.seconds) }
            runCurrent()

            // When
            publication.cancelAndJoin()

            // Then
            assertTrue(publication.isCancelled)
            val second = async { adapter.publish(item, 10.seconds) }
            runCurrent()
            registry.dispatch(NanoConfirmationEvent(item.hash, "inactive"))
            second.await()
        }

    @Test
    fun `WebSocket failure remains sticky for later publications`() =
        runTest {
            // Given
            val registry = NanoConfirmationRegistry()
            registry.failAll(NanoAdapterException("confirmation WebSocket closed"))
            val rpc = FakeRpc { _, _ -> error("RPC must not run after terminal WebSocket failure") }
            val adapter = NanoPostCementAdapter(rpc, registry)

            // When
            val error =
                assertFailsWith<NanoAdapterException> {
                    adapter.publish(publicationItem(), 1.seconds)
                }

            // Then
            assertEquals("confirmation WebSocket closed", error.message)
            assertEquals(0, rpc.calls)
        }

    @Test
    fun `confirmation parser ignores unrelated and malformed messages`() {
        // Given
        val json = Json.Default

        // When
        val confirmation =
            NanoConfirmationRegistry.parse(
                json,
                """{"topic":"confirmation","message":{"hash":"ABC","confirmation_type":"inactive"}}""",
            )

        // Then
        assertEquals(NanoConfirmationEvent("ABC", "inactive"), confirmation)
        assertEquals(null, NanoConfirmationRegistry.parse(json, "{}"))
        assertEquals(null, NanoConfirmationRegistry.parse(json, "not-json"))
    }

    private fun publicationItem(): BenchmarkItem<NanoPublication> {
        val block =
            NanoStateBlock(
                account = NanoFixtures.DEV_GENESIS_ACCOUNT,
                balance = "1",
                link = NanoFixtures.DEV_GENESIS_PUBLIC_KEY,
                previous = NanoFixtures.DEV_GENESIS_HASH,
                representative = NanoFixtures.DEV_GENESIS_ACCOUNT,
                signature = "0".repeat(128),
                work = "0".repeat(16),
            )
        return BenchmarkItem(
            lane = "lane",
            sequence = 0,
            account = block.account,
            hash = "A".repeat(64),
            payload = NanoPublication("send", block),
        )
    }

    private class FakeRpc(
        private val response: suspend (String, Map<String, JsonElement>) -> JsonObject,
    ) : NanoRpc {
        var calls = 0
            private set

        override suspend fun call(
            action: String,
            parameters: Map<String, JsonElement>,
        ): JsonObject {
            calls++
            return response(action, parameters)
        }
    }
}
