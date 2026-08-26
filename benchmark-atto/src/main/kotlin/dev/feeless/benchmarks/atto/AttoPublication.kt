package dev.feeless.benchmarks.atto

/** A transaction body encoded before the benchmark clock starts. */
class AttoPublication internal constructor(
    private val encodedTransaction: ByteArray,
) {
    internal fun body(): ByteArray = encodedTransaction

    internal fun copyEncodedTransaction(): ByteArray = encodedTransaction.copyOf()
}
