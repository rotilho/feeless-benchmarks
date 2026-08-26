package dev.feeless.benchmarks.atto

import com.sun.net.httpserver.HttpServer
import dev.feeless.benchmarks.core.BenchmarkItem
import dev.feeless.benchmarks.core.VIRTUAL
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AttoPublisherTest {
    @Test
    fun `Apache5 leaves timing to the publication timeout`() {
        // Given
        val config = Apache5EngineConfig()

        // When
        config.configureAttoPublishing()

        // Then
        assertEquals(Dispatchers.VIRTUAL, config.dispatcher)
        assertEquals(0, config.socketTimeout)
        assertEquals(0, config.connectTimeout)
        assertEquals(0, config.connectionRequestTimeout)
    }

    @Test
    fun `default publisher reuses a pooled connection for POST requests`() =
        runBlocking {
            // Given
            val item = fixtureItem()
            val response = item.payload.copyEncodedTransaction() + byteArrayOf('\n'.code.toByte())
            val remotePorts = ConcurrentHashMap.newKeySet<Int>()
            val requestCount = AtomicInteger()
            val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
            val executor = Executors.newVirtualThreadPerTaskExecutor()
            server.executor = executor
            server.createContext("/transactions/stream") { exchange ->
                exchange.requestBody.use { it.readAllBytes() }
                remotePorts += exchange.remoteAddress.port
                requestCount.incrementAndGet()
                exchange.responseHeaders.add(HttpHeaders.ContentType, "application/x-ndjson")
                exchange.sendResponseHeaders(HttpStatusCode.OK.value, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()

            try {
                val baseUrl = "http://${InetAddress.getLoopbackAddress().hostAddress}:${server.address.port}"

                // When
                AttoPublisher(baseUrl).use { publisher ->
                    repeat(200) { publisher.publish(item, 5.seconds) }
                }

                // Then
                assertEquals(200, requestCount.get())
                assertEquals(1, remotePorts.size)
            } finally {
                server.stop(0)
                executor.close()
            }
        }

    @Test
    fun `posts the pre-encoded body once and accepts one matching LF-terminated object`() =
        runBlocking {
            // Given
            val item = fixtureItem()
            var requestBody: ByteArray? = null
            var requestCount = 0
            val engine =
                MockEngine { request ->
                    requestCount++
                    val outgoingBody = request.body as OutgoingContent.ByteArrayContent
                    requestBody = outgoingBody.bytes()
                    assertEquals(ContentType.Application.Json, outgoingBody.contentType)
                    assertEquals("application/x-ndjson", request.headers[HttpHeaders.Accept])
                    respond(
                        content = ByteReadChannel(item.payload.copyEncodedTransaction() + byteArrayOf('\n'.code.toByte())),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "Application/X-NDJSON; charset=utf-8"),
                    )
                }

            // When
            AttoPublisher("http://node.example/", HttpClient(engine)).use { publisher ->
                publisher.publish(item, 1.seconds)
            }

            // Then
            assertEquals(1, requestCount)
            assertContentEquals(item.payload.copyEncodedTransaction(), requestBody)
        }

    @Test
    fun `rejects every strict stream contract violation without retry`() =
        runBlocking {
            // Given
            val item = fixtureItem()
            val validBody = item.payload.copyEncodedTransaction() + byteArrayOf('\n'.code.toByte())
            val cases =
                listOf(
                    FailureCase("status", HttpStatusCode.Accepted, "application/x-ndjson", byteArrayOf(), "status 202"),
                    FailureCase("media type", HttpStatusCode.OK, "application/json", validBody, "media type"),
                    FailureCase("missing media type", HttpStatusCode.OK, null, validBody, "<missing>"),
                    FailureCase("malformed", HttpStatusCode.OK, "application/x-ndjson", "{bad}\n".encodeToByteArray(), "malformed JSON"),
                    FailureCase("multiple", HttpStatusCode.OK, "application/x-ndjson", validBody + validBody, "exactly one"),
                    FailureCase("empty", HttpStatusCode.OK, "application/x-ndjson", "\n".encodeToByteArray(), "exactly one"),
                    FailureCase(
                        "leading blank",
                        HttpStatusCode.OK,
                        "application/x-ndjson",
                        byteArrayOf('\n'.code.toByte()) + validBody,
                        "exactly one",
                    ),
                    FailureCase(
                        "trailing bytes",
                        HttpStatusCode.OK,
                        "application/x-ndjson",
                        validBody + " ".encodeToByteArray(),
                        "LF-terminated",
                    ),
                    FailureCase(
                        "CRLF",
                        HttpStatusCode.OK,
                        "application/x-ndjson",
                        item.payload.copyEncodedTransaction() + "\r\n".encodeToByteArray(),
                        "surrounding whitespace",
                    ),
                    FailureCase("non-object", HttpStatusCode.OK, "application/x-ndjson", "[]\n".encodeToByteArray(), "must be an object"),
                    FailureCase(
                        "missing LF",
                        HttpStatusCode.OK,
                        "application/x-ndjson",
                        item.payload.copyEncodedTransaction(),
                        "LF-terminated",
                    ),
                )

            for (case in cases) {
                var requestCount = 0
                val engine =
                    MockEngine {
                        requestCount++
                        respond(
                            content = ByteReadChannel(case.body),
                            status = case.status,
                            headers = case.contentType?.let { headersOf(HttpHeaders.ContentType, it) } ?: Headers.Empty,
                        )
                    }

                // When
                val error =
                    assertFailsWith<AttoPublisherException>(case.name) {
                        AttoPublisher("http://node.example", HttpClient(engine)).use { publisher ->
                            publisher.publish(item, 1.seconds)
                        }
                    }

                // Then
                assertEquals(true, error.message.orEmpty().contains(case.expectedMessage), case.name)
                assertEquals(1, requestCount, case.name)
            }
        }

    @Test
    fun `rejects a valid returned transaction when its hash differs`() =
        runBlocking {
            // Given
            val fixtureItem = fixtureItem()
            val mismatchedItem = fixtureItem.copy(hash = "F".repeat(64))
            val engine =
                MockEngine {
                    respond(
                        content = ByteReadChannel(fixtureItem.payload.copyEncodedTransaction() + byteArrayOf('\n'.code.toByte())),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"),
                    )
                }

            // When
            val error =
                assertFailsWith<AttoPublisherException> {
                    AttoPublisher("http://node.example", HttpClient(engine)).use { publisher ->
                        publisher.publish(mismatchedItem, 1.seconds)
                    }
                }

            // Then
            assertEquals(true, error.message.orEmpty().contains("does not match"))
        }

    @Test
    fun `configured timeout becomes a publication error after one request`() =
        runBlocking {
            // Given
            val item = fixtureItem()
            var requestCount = 0
            val engine =
                MockEngine {
                    requestCount++
                    delay(10.seconds)
                    respond(
                        content = ByteReadChannel.Empty,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/x-ndjson"),
                    )
                }

            // When
            val error =
                assertFailsWith<AttoPublisherException> {
                    AttoPublisher("http://node.example", HttpClient(engine)).use { publisher ->
                        publisher.publish(item, 10.milliseconds)
                    }
                }

            // Then
            assertEquals(1, requestCount)
            assertEquals(true, "timed out" in error.message.orEmpty())
        }

    private fun fixtureItem(): BenchmarkItem<AttoPublication> {
        val fixtures = Path.of(System.getProperty("fixturesDirectory"))
        return AttoFixtures
            .loadScenario(
                fixture = "atto-genesis",
                initialPath = fixtures.resolve("atto-genesis-initial.zip"),
                benchmarkPath = fixtures.resolve("atto-genesis-benchmark.zip"),
            ).lanes.values
            .single()
            .first()
    }
}

private data class FailureCase(
    val name: String,
    val status: HttpStatusCode,
    val contentType: String?,
    val body: ByteArray,
    val expectedMessage: String,
)
