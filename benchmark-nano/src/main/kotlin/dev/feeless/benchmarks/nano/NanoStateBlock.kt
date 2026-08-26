package dev.feeless.benchmarks.nano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
data class NanoStateBlock(
    val type: String = "state",
    val account: String,
    val balance: String,
    val link: String,
    @SerialName("link_as_account")
    val linkAsAccount: String = NanoAccounts.encode(link.hexBytes(32)),
    val previous: String,
    val representative: String,
    val signature: String,
    val work: String,
) {
    fun hash(): String =
        hash(
            account = account,
            previous = previous,
            representative = representative,
            balance = balance,
            link = link,
        )

    fun hasValidSignature(): Boolean =
        NanoEd25519.verify(
            NanoAccounts.decode(account),
            hash().hexBytes(32),
            runCatching { signature.hexBytes(64) }.getOrElse { return false },
        )

    companion object {
        private val statePreamble = ByteArray(32).also { it[31] = 6 }
        private val maximumBalance = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

        fun create(
            privateKey: ByteArray,
            previous: String,
            representative: String,
            balance: BigInteger,
            link: String,
            work: String,
        ): NanoStateBlock {
            val account = NanoAccounts.encode(NanoEd25519.publicKey(privateKey))
            val hash = hash(account, previous, representative, balance.toString(), link)
            return NanoStateBlock(
                account = account,
                balance = balance.toString(),
                link = link.uppercase(),
                previous = previous.uppercase().padStart(64, '0'),
                representative = representative,
                signature = NanoEd25519.sign(privateKey, hash.hexBytes(32)).upperHex(),
                work = work.uppercase(),
            )
        }

        fun hash(
            account: String,
            previous: String,
            representative: String,
            balance: String,
            link: String,
        ): String {
            val rawBalance =
                balance.toBigIntegerOrNull()
                    ?: throw IllegalArgumentException("balance must be an integer")
            require(rawBalance in BigInteger.ZERO..maximumBalance) { "balance must fit an unsigned 128-bit integer" }
            val balanceBytes = rawBalance.toUnsignedFixedBytes(16)
            return blake2b(
                32,
                statePreamble,
                NanoAccounts.decode(account),
                previous.padStart(64, '0').hexBytes(32),
                NanoAccounts.decode(representative),
                balanceBytes,
                link.hexBytes(32),
            ).upperHex()
        }

        private fun BigInteger.toUnsignedFixedBytes(size: Int): ByteArray {
            val encoded =
                toByteArray().let {
                    if (it.size > 1 && it.first() == 0.toByte()) it.copyOfRange(1, it.size) else it
                }
            require(encoded.size <= size) { "integer does not fit in $size bytes" }
            return ByteArray(size).also { encoded.copyInto(it, size - encoded.size) }
        }
    }
}
