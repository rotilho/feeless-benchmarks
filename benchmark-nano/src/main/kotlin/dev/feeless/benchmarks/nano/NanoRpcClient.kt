package dev.feeless.benchmarks.nano

import dev.feeless.benchmarks.core.VIRTUAL
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class NanoRpcException(
    message: String,
) : RuntimeException(message)

internal interface NanoRpc {
    suspend fun call(
        action: String,
        parameters: Map<String, JsonElement> = emptyMap(),
    ): JsonObject
}

internal class NanoRpcClient(
    private val client: HttpClient,
    private val url: String,
    private val json: Json,
) : NanoRpc {
    override suspend fun call(
        action: String,
        parameters: Map<String, JsonElement>,
    ): JsonObject {
        val payload =
            buildJsonObject {
                put("action", action)
                parameters.forEach(::put)
            }
        val response =
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(payload))
            }
        val body = response.body<String>()
        if (response.status != HttpStatusCode.OK) {
            throw NanoRpcException("$action: HTTP ${response.status.value}: $body")
        }
        val result =
            runCatching { json.parseToJsonElement(body) as JsonObject }.getOrElse { error ->
                throw NanoRpcException("$action: invalid JSON: ${error.message}")
            }
        result["error"]?.jsonPrimitive?.content?.let { error ->
            throw NanoRpcException("$action: $error")
        }
        return result
    }
}

internal fun nanoRpcHttpClient(): HttpClient =
    HttpClient(Apache5) {
        followRedirects = false
        engine {
            configureNanoRpcPublishing()
        }
    }

internal fun Apache5EngineConfig.configureNanoRpcPublishing() {
    dispatcher = Dispatchers.VIRTUAL
    socketTimeout = 0
    connectTimeout = 0
    connectionRequestTimeout = 0
    configureConnectionManager {
        setMaxConnTotal(NANO_RPC_MAX_CONNECTIONS)
        setMaxConnPerRoute(NANO_RPC_MAX_CONNECTIONS)
    }
}

internal const val NANO_RPC_MAX_CONNECTIONS = 500
