package dev.feeless.benchmarks.nano

import dev.feeless.benchmarks.core.BenchmarkItem
import dev.feeless.benchmarks.core.PublishAdapter
import dev.feeless.benchmarks.core.VIRTUAL
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NanoAdapterException(
    message: String,
) : RuntimeException(message)

class NanoPostCementAdapter internal constructor(
    private val rpc: NanoRpc,
    private val confirmations: NanoConfirmationRegistry,
    private val closeResources: () -> Unit = {},
) : PublishAdapter<NanoPublication>,
    AutoCloseable {
    override suspend fun publish(
        item: BenchmarkItem<NanoPublication>,
        timeout: Duration,
    ) {
        val confirmation = confirmations.register(item.hash)
        try {
            val completed =
                withTimeoutOrNull(timeout) {
                    val response =
                        rpc.call(
                            "process",
                            mapOf(
                                "json_block" to processJson.encodeToJsonElement("true"),
                                "subtype" to processJson.encodeToJsonElement(item.payload.subtype),
                                "block" to processJson.encodeToJsonElement(item.payload.block),
                            ),
                        )
                    val returnedHash = response["hash"]?.jsonPrimitive?.content
                    if (returnedHash != item.hash) {
                        throw NanoAdapterException("process hash mismatch: predicted ${item.hash}, got $returnedHash")
                    }
                    val event = confirmation.await()
                    if (event.hash != item.hash) {
                        throw NanoAdapterException("confirmation hash mismatch: expected ${item.hash}, got ${event.hash}")
                    }
                    if (event.confirmationType !in POST_CEMENT_TYPES) {
                        throw NanoAdapterException(
                            "${item.hash} confirmation_type=${event.confirmationType}; expected one of ${POST_CEMENT_TYPES.sorted()}",
                        )
                    }
                    true
                }
            if (completed != true) throw NanoAdapterException("publication ${item.hash} timed out after $timeout")
        } finally {
            confirmations.discard(item.hash, confirmation)
        }
    }

    override fun close() = closeResources()

    companion object {
        private val POST_CEMENT_TYPES = setOf("active_quorum", "active_confirmation_height", "inactive")
        private val connectionTimeout = 10.seconds
        private val json = Json { ignoreUnknownKeys = true }
        private val processJson = Json { encodeDefaults = true }

        suspend fun connect(
            rpcUrl: String,
            websocketUrl: String,
        ): NanoPostCementAdapter {
            val rpcClient = nanoRpcHttpClient()
            val websocketClient =
                try {
                    HttpClient(CIO) {
                        install(WebSockets)
                        engine {
                            dispatcher = Dispatchers.VIRTUAL
                            requestTimeout = 0
                        }
                    }
                } catch (error: Throwable) {
                    try {
                        rpcClient.close()
                    } catch (closeError: Throwable) {
                        error.addSuppressed(closeError)
                    }
                    throw error
                }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.VIRTUAL)
            try {
                val socket = withTimeout(connectionTimeout) { websocketClient.webSocketSession(websocketUrl) }
                socket.send(
                    json.encodeToString(
                        buildJsonObject {
                            put("action", "subscribe")
                            put("topic", "confirmation")
                            put("ack", true)
                        },
                    ),
                )
                val acknowledgement =
                    withTimeout(connectionTimeout) {
                        when (val frame = socket.incoming.receive()) {
                            is Frame.Text -> frame.readText()
                            is Frame.Binary -> frame.readBytes().decodeToString()
                            else -> throw NanoAdapterException("unexpected WebSocket acknowledgement frame")
                        }
                    }
                val acknowledgementJson =
                    runCatching { json.parseToJsonElement(acknowledgement).jsonObject }.getOrElse {
                        throw NanoAdapterException("invalid WebSocket acknowledgement: $acknowledgement")
                    }
                if (acknowledgementJson["ack"]?.jsonPrimitive?.content != "subscribe") {
                    throw NanoAdapterException("unexpected WebSocket acknowledgement: $acknowledgement")
                }

                val registry = NanoConfirmationRegistry()
                val listener =
                    scope.launch {
                        try {
                            for (frame in socket.incoming) {
                                val payload =
                                    when (frame) {
                                        is Frame.Text -> frame.readText()
                                        is Frame.Binary -> frame.readBytes().decodeToString()
                                        else -> continue
                                    }
                                NanoConfirmationRegistry.parse(json, payload)?.let(registry::dispatch)
                            }
                            registry.failAll(NanoAdapterException("confirmation WebSocket closed"))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            registry.failAll(NanoAdapterException("confirmation listener failed: ${error.message}"))
                        }
                    }
                val rpc = NanoRpcClient(rpcClient, rpcUrl, json)
                return NanoPostCementAdapter(rpc, registry) {
                    listener.cancel()
                    socket.cancel()
                    scope.cancel()
                    closeHttpClients(rpcClient, websocketClient)
                }
            } catch (error: Throwable) {
                scope.cancel()
                try {
                    closeHttpClients(rpcClient, websocketClient)
                } catch (closeError: Throwable) {
                    error.addSuppressed(closeError)
                }
                throw error
            }
        }

        private fun closeHttpClients(
            rpcClient: HttpClient,
            websocketClient: HttpClient,
        ) {
            var failure: Throwable? = null
            for (client in listOf(websocketClient, rpcClient)) {
                try {
                    client.close()
                } catch (error: Throwable) {
                    val previous = failure
                    if (previous == null) failure = error else previous.addSuppressed(error)
                }
            }
            failure?.let { throw it }
        }
    }
}
