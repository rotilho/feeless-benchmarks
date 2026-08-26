package dev.feeless.benchmarks.nano

import org.bouncycastle.crypto.digests.Blake2bDigest

internal fun blake2b(
    sizeBytes: Int,
    vararg inputs: ByteArray,
): ByteArray {
    require(sizeBytes in 1..64) { "BLAKE2b output must be between 1 and 64 bytes" }
    val digest = Blake2bDigest(sizeBytes * 8)
    inputs.forEach { digest.update(it, 0, it.size) }
    return ByteArray(sizeBytes).also { digest.doFinal(it, 0) }
}

internal fun String.hexBytes(expectedBytes: Int? = null): ByteArray {
    require(length % 2 == 0) { "hex value must contain an even number of characters" }
    if (expectedBytes != null) {
        require(length == expectedBytes * 2) { "expected ${expectedBytes * 2} hex characters, got $length" }
    }
    return ByteArray(length / 2) { index ->
        val offset = index * 2
        val high = this[offset].digitToIntOrNull(16)
        val low = this[offset + 1].digitToIntOrNull(16)
        require(high != null && low != null) { "invalid hex value" }
        ((high shl 4) or low).toByte()
    }
}

internal fun ByteArray.upperHex(): String = joinToString(separator = "") { "%02X".format(it.toInt() and 0xff) }
