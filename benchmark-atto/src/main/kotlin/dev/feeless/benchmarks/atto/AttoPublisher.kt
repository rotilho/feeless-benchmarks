package dev.feeless.benchmarks.atto

import cash.atto.commons.AttoTransaction
import dev.feeless.benchmarks.core.BenchmarkItem
import dev.feeless.benchmarks.core.PublishAdapter
import dev.feeless.benchmarks.core.VIRTUAL
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

class AttoPublisherException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** One-shot final-stream publisher. It deliberately has no retry or fallback path. */
class AttoPublisher(
    baseUrl: String,
    private val client: HttpClient =
        HttpClient(Apache5) {
            followRedirects = false
            engine {
                configureAttoPublishing()
            }
        },
) : PublishAdapter<AttoPublication>,
    AutoCloseable {
    private val streamUrl = "${baseUrl.trimEnd('/')}/transactions/stream"

    override suspend fun publish(
        item: BenchmarkItem<AttoPublication>,
        timeout: Duration,
    ) {
        val returnedTransaction =
            withTimeoutOrNull(timeout) {
                val response =
                    client.post(streamUrl) {
                        header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        accept(NDJSON_CONTENT_TYPE)
                        setBody(item.payload.body())
                    }

                if (response.status != HttpStatusCode.OK) {
                    throw AttoPublisherException(
                        "POST /transactions/stream returned status ${response.status.value}; expected 200",
                    )
                }

                val responseMediaType =
                    response.headers[HttpHeaders.ContentType]
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()
                        .orEmpty()
                if (responseMediaType != NDJSON_MEDIA_TYPE) {
                    throw AttoPublisherException(
                        "POST /transactions/stream returned media type " +
                            "${responseMediaType.ifEmpty { "<missing>" }}; expected $NDJSON_MEDIA_TYPE",
                    )
                }

                decodeSingleTransaction(response.bodyAsBytes())
            } ?: throw AttoPublisherException("publication ${item.hash} timed out after $timeout")

        val returnedHash = returnedTransaction.hash.toString()
        if (returnedHash != item.hash) {
            throw AttoPublisherException(
                "returned transaction $returnedHash does not match submitted transaction ${item.hash}",
            )
        }
    }

    override fun close() {
        client.close()
    }
}

internal fun Apache5EngineConfig.configureAttoPublishing() {
    dispatcher = Dispatchers.VIRTUAL
    socketTimeout = 0
    connectTimeout = 0
    connectionRequestTimeout = 0
    configureConnectionManager {
        setMaxConnTotal(MAX_CONCURRENT_ACCOUNTS)
        setMaxConnPerRoute(MAX_CONCURRENT_ACCOUNTS)
    }
}

private fun decodeSingleTransaction(body: ByteArray): AttoTransaction {
    if (body.isEmpty() || body.last() != '\n'.code.toByte()) {
        throw AttoPublisherException("transaction stream object must be LF-terminated")
    }
    if (body.count { it == '\n'.code.toByte() } != 1 || body.size == 1) {
        throw AttoPublisherException("transaction stream must contain exactly one LF-terminated object")
    }

    val line = body.copyOf(body.lastIndex)
    val text =
        try {
            line.decodeUtf8Strict()
        } catch (error: Exception) {
            throw AttoPublisherException("transaction stream returned malformed UTF-8", error)
        }
    if (text.trim() != text) {
        throw AttoPublisherException("transaction stream object must not have surrounding whitespace")
    }
    val element =
        try {
            Json.parseToJsonElement(text)
        } catch (error: Exception) {
            throw AttoPublisherException("transaction stream returned malformed JSON", error)
        }
    if (element !is JsonObject) {
        throw AttoPublisherException("transaction stream JSON value must be an object")
    }

    return try {
        AttoTransaction.fromJson(text)
    } catch (error: Exception) {
        throw AttoPublisherException("transaction stream returned an invalid Atto transaction", error)
    }
}

private const val NDJSON_MEDIA_TYPE = "application/x-ndjson"
internal const val MAX_CONCURRENT_ACCOUNTS = 500
private val NDJSON_CONTENT_TYPE = ContentType.parse(NDJSON_MEDIA_TYPE)
