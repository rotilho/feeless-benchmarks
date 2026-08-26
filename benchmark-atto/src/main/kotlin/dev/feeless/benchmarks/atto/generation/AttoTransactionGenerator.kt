@file:OptIn(ExperimentalTime::class)

package dev.feeless.benchmarks.atto.generation

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.toAtto
import cash.atto.commons.toAttoVersion
import cash.atto.commons.worker.AttoWorker
import dev.feeless.benchmarks.atto.AttoFixtureSpec
import dev.feeless.benchmarks.core.VIRTUAL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal class AttoTransactionGenerator(
    private val spec: AttoFixtureSpec,
) : AutoCloseable {
    private val random = Random(spec.seed)
    private val baseTimestamp = Instant.parse(spec.baseTimestamp)
    private val worker = AttoWorker.cpu(1u.toUShort())
    private val accounts = linkedMapOf<AttoPublicKey, AccountState>()

    suspend fun generate(): GeneratedAttoFixture {
        check(accounts.isEmpty()) { "Generator instances can only be used once" }

        val initialTransactions = prepareAccounts()
        val benchmarkTransactions = generateBenchmarkTransactions()
        return GeneratedAttoFixture(initialTransactions, benchmarkTransactions)
    }

    private suspend fun prepareAccounts(): List<AttoTransaction> {
        val genesisPrivateKey = AttoPrivateKey(ByteArray(32))
        val genesisPublicKey = genesisPrivateKey.toPublicKey()
        val receivable =
            AttoReceivable(
                network = AttoNetwork.LOCAL,
                hash = AttoHash(ByteArray(32)),
                version = 0u.toAttoVersion(),
                algorithm = AttoAlgorithm.V1,
                publicKey = AttoPublicKey(ByteArray(32)),
                timestamp = (baseTimestamp - 1.seconds).toAtto(),
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = genesisPublicKey,
                amount = AttoAmount.MAX,
            )
        val (genesisBlock, genesisAccount) =
            AttoAccount.open(
                representativeAlgorithm = AttoAlgorithm.V1,
                representativePublicKey = genesisPublicKey,
                receivable = receivable,
                timestamp = baseTimestamp.toAtto(),
            )
        val genesisTransaction = genesisBlock.toTransaction(genesisPrivateKey)
        accounts[genesisPublicKey] = AccountState(genesisPrivateKey, genesisAccount)

        val transactions = ArrayList<AttoTransaction>(1 + 2 * (spec.accountCount - 1))
        transactions += genesisTransaction
        val initialAmount = AttoAmount(AttoAmount.MAX.raw / spec.accountCount.toULong())
        val firstSetupTimestamp = baseTimestamp + 1.milliseconds

        repeat(spec.accountCount - 1) { index ->
            val receiverPrivateKey = nextPrivateKey()
            val receiverPublicKey = receiverPrivateKey.toPublicKey()
            accounts[receiverPublicKey] = AccountState(receiverPrivateKey, null)

            val sendTransaction =
                send(
                    senderPublicKey = genesisPublicKey,
                    receiverPublicKey = receiverPublicKey,
                    amount = initialAmount,
                    timestamp = (firstSetupTimestamp + (index * 2).milliseconds).toAtto(),
                )
            transactions += sendTransaction

            transactions +=
                receive(
                    receivable = (sendTransaction.block as AttoSendBlock).toReceivable(),
                    timestamp = (firstSetupTimestamp + (index * 2 + 1).milliseconds).toAtto(),
                )
        }

        return transactions
    }

    private suspend fun generateBenchmarkTransactions(): List<AttoTransaction> =
        coroutineScope {
            val receiverPublicKey = AttoPublicKey(ByteArray(32))
            val accountStates = accounts.values.toList()
            val firstBenchmarkTimestamp = baseTimestamp + (spec.accountCount * 2 + 1).milliseconds

            accountStates
                .map { initialState ->
                    async(Dispatchers.VIRTUAL) {
                        generateBenchmarkLane(
                            initialState = initialState,
                            receiverPublicKey = receiverPublicKey,
                            firstBenchmarkTimestamp = firstBenchmarkTimestamp,
                        )
                    }
                }.awaitAll()
                .flatten()
        }

    private suspend fun generateBenchmarkLane(
        initialState: AccountState,
        receiverPublicKey: AttoPublicKey,
        firstBenchmarkTimestamp: Instant,
    ): List<AttoTransaction> =
        AttoWorker.cpu(1u.toUShort()).use { laneWorker ->
            var account = checkNotNull(initialState.account) { "Sender account has not been opened" }
            val transactionsPerAccount = spec.transactionCount / spec.accountCount
            val transactions = ArrayList<AttoTransaction>(transactionsPerAccount)

            repeat(transactionsPerAccount) { sequence ->
                val (block, updatedAccount) =
                    account.send(
                        receiverAlgorithm = AttoAlgorithm.V1,
                        receiverPublicKey = receiverPublicKey,
                        amount = AttoAmount(1UL),
                        timestamp = (firstBenchmarkTimestamp + sequence.milliseconds).toAtto(),
                    )
                account = updatedAccount
                transactions += block.toTransaction(initialState.privateKey, laneWorker)
            }

            transactions
        }

    private suspend fun send(
        senderPublicKey: AttoPublicKey,
        receiverPublicKey: AttoPublicKey,
        amount: AttoAmount,
        timestamp: cash.atto.commons.AttoInstant,
    ): AttoTransaction {
        val state = accounts.getValue(senderPublicKey)
        val account = checkNotNull(state.account) { "Sender account has not been opened" }
        val (block, updatedAccount) =
            account.send(
                receiverAlgorithm = AttoAlgorithm.V1,
                receiverPublicKey = receiverPublicKey,
                amount = amount,
                timestamp = timestamp,
            )
        accounts[senderPublicKey] = state.copy(account = updatedAccount)
        return block.toTransaction(state.privateKey)
    }

    private suspend fun receive(
        receivable: AttoReceivable,
        timestamp: cash.atto.commons.AttoInstant,
    ): AttoTransaction {
        val state = accounts.getValue(receivable.receiverPublicKey)
        val (block, updatedAccount) =
            if (state.account == null) {
                AttoAccount.open(
                    representativeAlgorithm = AttoAlgorithm.V1,
                    representativePublicKey = accounts.keys.first(),
                    receivable = receivable,
                    timestamp = timestamp,
                )
            } else {
                state.account.receive(receivable, timestamp)
            }
        accounts[receivable.receiverPublicKey] = state.copy(account = updatedAccount)
        return block.toTransaction(state.privateKey)
    }

    private fun nextPrivateKey(): AttoPrivateKey {
        val bytes = random.nextBytes(ByteArray(32))
        if (bytes.all { it == 0.toByte() }) bytes[31] = 1
        return AttoPrivateKey(bytes)
    }

    private suspend fun AttoBlock.toTransaction(
        privateKey: AttoPrivateKey,
        transactionWorker: AttoWorker = worker,
    ): AttoTransaction =
        AttoTransaction(
            block = this,
            signature = privateKey.sign(hash),
            work = transactionWorker.work(this),
        )

    override fun close() {
        worker.close()
    }
}

private data class AccountState(
    val privateKey: AttoPrivateKey,
    val account: AttoAccount?,
)
