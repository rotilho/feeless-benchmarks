package dev.feeless.benchmarks.nano

import java.math.BigInteger

object NanoAccounts {
    private const val ALPHABET = "13456789abcdefghijkmnopqrstuwxyz"
    private const val PUBLIC_KEY_ENCODED_LENGTH = 52
    private const val CHECKSUM_ENCODED_LENGTH = 8
    private val alphabetIndex = ALPHABET.withIndex().associate { it.value to it.index }

    fun encode(publicKey: ByteArray): String {
        require(publicKey.size == 32) { "Nano public key must contain 32 bytes" }
        val encodedPublicKey = encodeBase32(BigInteger(1, publicKey), PUBLIC_KEY_ENCODED_LENGTH)
        val checksum = blake2b(5, publicKey).reversedArray()
        val encodedChecksum = encodeBase32(BigInteger(1, checksum), CHECKSUM_ENCODED_LENGTH)
        return "nano_$encodedPublicKey$encodedChecksum"
    }

    fun decode(account: String): ByteArray {
        require(isValid(account)) { "invalid Nano account: $account" }
        val body = account.removePrefix("nano_").removePrefix("xrb_")
        return decodePublicKey(body.take(PUBLIC_KEY_ENCODED_LENGTH))
    }

    fun isValid(account: String): Boolean {
        val prefixLength =
            when {
                account.startsWith("nano_") -> 5
                account.startsWith("xrb_") -> 4
                else -> return false
            }
        val body = account.substring(prefixLength)
        if (body.length != PUBLIC_KEY_ENCODED_LENGTH + CHECKSUM_ENCODED_LENGTH) return false
        if (body.any { it !in alphabetIndex }) return false

        val publicKey = runCatching { decodePublicKey(body.take(PUBLIC_KEY_ENCODED_LENGTH)) }.getOrNull() ?: return false
        val expectedChecksum =
            encodeBase32(
                BigInteger(1, blake2b(5, publicKey).reversedArray()),
                CHECKSUM_ENCODED_LENGTH,
            )
        return body.takeLast(CHECKSUM_ENCODED_LENGTH) == expectedChecksum
    }

    private fun decodePublicKey(encoded: String): ByteArray {
        val value =
            encoded.fold(BigInteger.ZERO) { result, character ->
                result.shiftLeft(5).or(BigInteger.valueOf(alphabetIndex.getValue(character).toLong()))
            }
        require(value.bitLength() <= 256) { "Nano account has non-zero padding bits" }
        val source = value.toByteArray().let { if (it.size == 33 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        require(source.size <= 32) { "Nano account public key is too large" }
        return ByteArray(32).also { source.copyInto(it, destinationOffset = 32 - source.size) }
    }

    private fun encodeBase32(
        value: BigInteger,
        outputLength: Int,
    ): String =
        buildString(outputLength) {
            repeat(outputLength) { index ->
                val shift = (outputLength - index - 1) * 5
                append(ALPHABET[value.shiftRight(shift).and(BigInteger.valueOf(31)).toInt()])
            }
        }
}
