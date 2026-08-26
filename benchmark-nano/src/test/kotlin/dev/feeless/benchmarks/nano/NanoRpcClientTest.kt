package dev.feeless.benchmarks.nano

import com.sun.net.httpserver.HttpServer
import dev.feeless.benchmarks.core.VIRTUAL
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NanoRpcClientTest {
    @Test
    fun `Apache RPC transport uses virtual dispatch and leaves timing to the caller`() {
        // Given
        val config = Apache5EngineConfig()

        // When
        config.configureNanoRpcPublishing()

        // Then
        assertEquals(Dispatchers.VIRTUAL, config.dispatcher)
        assertEquals(0, config.socketTimeout)
        assertEquals(0L, config.connectTimeout)
        assertEquals(0L, config.connectionRequestTimeout)
    }

    @Test
    fun `Apache RPC transport reuses connections for POST`() {
        // Given
        val requests = AtomicInteger()
        val remotePorts = ConcurrentHashMap.newKeySet<Int>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        server.executor = executor
        server.createContext("/") { exchange ->
            exchange.requestBody.use { it.readAllBytes() }
            requests.incrementAndGet()
            remotePorts += exchange.remoteAddress.port
            val response = "{\"ok\":\"true\"}".encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val client = nanoRpcHttpClient()
        val rpc = NanoRpcClient(client, "http://127.0.0.1:${server.address.port}/", Json.Default)

        try {
            // When
            runBlocking {
                repeat(50) {
                    rpc.call("version")
                }
            }

            // Then
            assertEquals(50, requests.get())
            assertEquals(1, remotePorts.size)
        } finally {
            client.close()
            server.stop(0)
            executor.close()
        }
    }

    @Test
    fun `RPC accepts only status 200 JSON without an error field`() =
        runTest {
            // Given
            val engine =
                MockEngine { request ->
                    assertEquals("http://node.test/", request.url.toString())
                    respond(
                        content = """{"hash":"ABC"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val client = HttpClient(engine)

            try {
                // When
                val response =
                    NanoRpcClient(client, "http://node.test/", Json.Default).call(
                        "process",
                        mapOf("subtype" to JsonPrimitive("send")),
                    )

                // Then
                assertEquals("ABC", response.getValue("hash").jsonPrimitive.content)
            } finally {
                client.close()
            }
        }

    @Test
    fun `RPC surfaces HTTP JSON and protocol errors`() =
        runTest {
            // Given
            val httpClient = HttpClient(MockEngine { respond("no", HttpStatusCode.BadGateway) })
            val protocolClient =
                HttpClient(
                    MockEngine {
                        respond(
                            """{"error":"Block gap previous"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    },
                )

            try {
                // When
                val httpError =
                    assertFailsWith<NanoRpcException> {
                        NanoRpcClient(httpClient, "http://node.test", Json.Default).call("process")
                    }
                val protocolError =
                    assertFailsWith<NanoRpcException> {
                        NanoRpcClient(protocolClient, "http://node.test", Json.Default).call("process")
                    }

                // Then
                assertTrue("HTTP 502" in httpError.message.orEmpty())
                assertTrue("Block gap previous" in protocolError.message.orEmpty())
            } finally {
                httpClient.close()
                protocolClient.close()
            }
        }
}
