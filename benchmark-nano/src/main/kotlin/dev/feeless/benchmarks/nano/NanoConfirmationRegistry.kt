package dev.feeless.benchmarks.nano

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal data class NanoConfirmationEvent(
    val hash: String,
    val confirmationType: String?,
)

internal class NanoConfirmationRegistry {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<NanoConfirmationEvent>>()
    private val terminalFailure = AtomicReference<Throwable?>()

    fun register(hash: String): CompletableDeferred<NanoConfirmationEvent> {
        terminalFailure.get()?.let { throw it }
        val confirmation = CompletableDeferred<NanoConfirmationEvent>()
        check(pending.putIfAbsent(hash, confirmation) == null) { "confirmation already pending for $hash" }
        terminalFailure.get()?.let { error ->
            if (pending.remove(hash, confirmation)) confirmation.completeExceptionally(error)
        }
        return confirmation
    }

    fun dispatch(event: NanoConfirmationEvent) {
        pending[event.hash]?.complete(event)
    }

    fun discard(
        hash: String,
        confirmation: CompletableDeferred<NanoConfirmationEvent>,
    ) {
        if (pending.remove(hash, confirmation)) confirmation.cancel()
    }

    fun failAll(error: Throwable) {
        terminalFailure.compareAndSet(null, error)
        val terminal = requireNotNull(terminalFailure.get())
        pending.values.forEach { it.completeExceptionally(terminal) }
    }

    companion object {
        fun parse(
            json: Json,
            payload: String,
        ): NanoConfirmationEvent? {
            val root = runCatching { json.parseToJsonElement(payload) as JsonObject }.getOrNull() ?: return null
            if (root["topic"]?.jsonPrimitive?.content != "confirmation") return null
            val message = runCatching { root.getValue("message").jsonObject }.getOrNull() ?: return null
            val hash = runCatching { message.getValue("hash").jsonPrimitive.content }.getOrNull() ?: return null
            val confirmationType = runCatching { message.getValue("confirmation_type").jsonPrimitive.content }.getOrNull()
            return NanoConfirmationEvent(hash, confirmationType)
        }
    }
}
