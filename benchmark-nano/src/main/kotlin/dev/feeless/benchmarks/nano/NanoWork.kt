package dev.feeless.benchmarks.nano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NanoLedgerEpoch {
    @SerialName("epoch_0")
    EPOCH_0,

    @SerialName("epoch_1")
    EPOCH_1,

    @SerialName("epoch_2")
    EPOCH_2,
}

@Serializable
enum class NanoBlockSubtype {
    @SerialName("send")
    SEND,

    @SerialName("receive")
    RECEIVE,

    @SerialName("change")
    CHANGE,

    @SerialName("epoch")
    EPOCH,
}

@Serializable
data class NanoThresholdProfile(
    val name: String,
    val source: String,
    @SerialName("epoch_0_and_1")
    val epoch0And1: String,
    @SerialName("epoch_2_send_change")
    val epoch2SendChange: String,
    @SerialName("epoch_2_receive_epoch")
    val epoch2ReceiveEpoch: String,
)

object NanoWork {
    const val EPOCH_0_AND_1 = "FE00000000000000"
    const val EPOCH_2_SEND_CHANGE = "FFC0000000000000"
    const val EPOCH_2_RECEIVE_EPOCH = "F000000000000000"

    val v28DevProfile =
        NanoThresholdProfile(
            name = "nano-v28.2-dev-work-v1",
            source = "https://github.com/nanocurrency/nano-node/blob/V28.2/nano/lib/constants.cpp#L38-L60",
            epoch0And1 = EPOCH_0_AND_1,
            epoch2SendChange = EPOCH_2_SEND_CHANGE,
            epoch2ReceiveEpoch = EPOCH_2_RECEIVE_EPOCH,
        )

    fun threshold(
        epoch: NanoLedgerEpoch,
        subtype: NanoBlockSubtype,
    ): ULong =
        when (epoch) {
            NanoLedgerEpoch.EPOCH_0,
            NanoLedgerEpoch.EPOCH_1,
            -> EPOCH_0_AND_1.toULong(16)

            NanoLedgerEpoch.EPOCH_2 ->
                when (subtype) {
                    NanoBlockSubtype.RECEIVE,
                    NanoBlockSubtype.EPOCH,
                    -> EPOCH_2_RECEIVE_EPOCH.toULong(16)

                    NanoBlockSubtype.SEND,
                    NanoBlockSubtype.CHANGE,
                    -> EPOCH_2_SEND_CHANGE.toULong(16)
                }
        }

    fun difficulty(
        root: String,
        work: String,
    ): ULong {
        val nonce = work.toULongOrNull(16) ?: throw IllegalArgumentException("work must be unsigned hexadecimal")
        val digest = blake2b(8, nonce.littleEndianBytes(), root.hexBytes(32))
        return digest.littleEndianULong()
    }

    fun isValid(
        root: String,
        work: String,
        epoch: NanoLedgerEpoch,
        subtype: NanoBlockSubtype,
    ): Boolean = runCatching { difficulty(root, work) >= threshold(epoch, subtype) }.getOrDefault(false)

    fun deterministic(
        root: String,
        epoch: NanoLedgerEpoch,
        subtype: NanoBlockSubtype,
    ): String {
        val rootBytes = root.hexBytes(32)
        val threshold = threshold(epoch, subtype)
        var nonce = blake2b(8, "nano-v28.2-work:$root".encodeToByteArray()).littleEndianULong()
        while (true) {
            val value = blake2b(8, nonce.littleEndianBytes(), rootBytes).littleEndianULong()
            if (value >= threshold) return nonce.toString(16).uppercase().padStart(16, '0')
            nonce++
        }
    }

    fun root(
        account: String,
        previous: String,
    ): String = if (previous.all { it == '0' }) NanoAccounts.decode(account).upperHex() else previous.uppercase()

    private fun ULong.littleEndianBytes(): ByteArray =
        ByteArray(8) { index ->
            (this shr (index * 8)).toByte()
        }

    private fun ByteArray.littleEndianULong(): ULong {
        require(size == 8) { "expected eight bytes" }
        return indices.reversed().fold(0uL) { result, index ->
            (result shl 8) or (this[index].toUByte().toULong())
        }
    }
}
