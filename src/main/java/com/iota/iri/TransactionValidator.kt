package com.iota.iri

import com.iota.iri.controllers.TipsViewModel
import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.controllers.TransactionViewModel.*
import com.iota.iri.hash.Sponge
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.network.TransactionRequester
import com.iota.iri.storage.Tangle
import com.iota.iri.zmq.MessageQ
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by paul on 4/17/17.
 */
class TransactionValidator(private val tangle: Tangle, private val tipsViewModel: TipsViewModel, private val transactionRequester: TransactionRequester,
                           private val messageQ: MessageQ, snapshotTimestamp: Long) {
    private val log = LoggerFactory.getLogger(TransactionValidator::class.java)
    var minWeightMagnitude = 81
        private set

    private var newSolidThread: Thread? = null

    private val useFirst = AtomicBoolean(true)
    private val shuttingDown = AtomicBoolean(false)
    private val cascadeSync = Any()
    private val newSolidTransactionsOne = LinkedHashSet<Hash>()
    private val newSolidTransactionsTwo = LinkedHashSet<Hash>()

    private val nextSubSolidGroup = AtomicInteger(1)

    init {
        TransactionValidator.snapshotTimestamp = snapshotTimestamp
        TransactionValidator.snapshotTimestampMs = snapshotTimestamp * 1000
    }

    fun init(testnet: Boolean, mwm: Int) {
        minWeightMagnitude = mwm

        //lowest allowed MWM encoded in 46 bytes.
        if (!testnet && minWeightMagnitude < 13) {
            minWeightMagnitude = 13
        }

        newSolidThread = Thread(spawnSolidTransactionsPropagation(), "Solid TX cascader")
        newSolidThread!!.start()
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        shuttingDown.set(true)
        newSolidThread!!.join()
    }

    @Throws(Exception::class)
    fun checkSolidity(hash: Hash, milestone: Boolean): Boolean {
        if (TransactionViewModel.fromHash(tangle, hash).isSolid) {
            return true
        }
        val analyzedHashes = HashSet(setOf(Hash.NULL_HASH))
        var solid = true
        val nonAnalyzedTransactions = LinkedList(setOf(hash))
        var hashPointer = nonAnalyzedTransactions.poll()
        while (hashPointer != null) {
            if (analyzedHashes.add(hashPointer)) {
                val transaction = TransactionViewModel.fromHash(tangle, hashPointer)
                if (!transaction.isSolid) {
                    if (transaction.type == TransactionViewModel.PREFILLED_SLOT && hashPointer != Hash.NULL_HASH) {
                        transactionRequester.requestTransaction(hashPointer, milestone)
                        solid = false
                        break
                    } else {
                        if (solid) {
                            nonAnalyzedTransactions.offer(transaction.trunkTransactionHash)
                            nonAnalyzedTransactions.offer(transaction.branchTransactionHash)
                        }
                    }
                }
            }
        }
        if (solid) {
            TransactionViewModel.updateSolidTransactions(tangle, analyzedHashes)
        }
        analyzedHashes.clear()
        return solid
    }

    fun addSolidTransaction(hash: Hash) {
        synchronized(cascadeSync) {
            if (useFirst.get()) {
                newSolidTransactionsOne.add(hash)
            } else {
                newSolidTransactionsTwo.add(hash)
            }
        }
    }

    private fun spawnSolidTransactionsPropagation(): Runnable {
        return Runnable {
            while (!shuttingDown.get()) {
                val newSolidHashes = HashSet<Hash>()
                useFirst.set(!useFirst.get())
                synchronized(cascadeSync) {
                    if (useFirst.get()) {
                        newSolidHashes.addAll(newSolidTransactionsTwo)
                    } else {
                        newSolidHashes.addAll(newSolidTransactionsOne)
                    }
                }
                val cascadeIterator = newSolidHashes.iterator()
                while (cascadeIterator.hasNext() && !shuttingDown.get()) {
                    try {
                        val hash = cascadeIterator.next()
                        val transaction = TransactionViewModel.fromHash(tangle, hash)
                        val approvers = transaction.getApprovers(tangle).getHashes()
                        for (h in approvers) {
                            val tx = TransactionViewModel.fromHash(tangle, h)
                            if (quietQuickSetSolid(tx)) {
                                tx.update(tangle, "solid")
                            } else {
                                if (transaction.isSolid) {
                                    addSolidTransaction(hash)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.error("Some error", e)
                        // TODO: Do something, maybe, or do nothing.
                    }

                }
                synchronized(cascadeSync) {
                    if (useFirst.get()) {
                        newSolidTransactionsTwo.clear()
                    } else {
                        newSolidTransactionsOne.clear()
                    }
                }
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

            }
        }
    }

    @Throws(Exception::class)
    fun updateStatus(transactionViewModel: TransactionViewModel) {
        transactionRequester.clearTransactionRequest(transactionViewModel.hash)
        if (transactionViewModel.getApprovers(tangle).size() == 0) {
            tipsViewModel.addTipHash(transactionViewModel.hash)
        }
        tipsViewModel.removeTipHash(transactionViewModel.trunkTransactionHash)
        tipsViewModel.removeTipHash(transactionViewModel.branchTransactionHash)

        if (quickSetSolid(transactionViewModel)) {
            addSolidTransaction(transactionViewModel.hash)
        }
    }

    fun quietQuickSetSolid(transactionViewModel: TransactionViewModel): Boolean {
        try {
            return quickSetSolid(transactionViewModel)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

    }

    @Throws(Exception::class)
    private fun quickSetSolid(transactionViewModel: TransactionViewModel): Boolean {
        if (!transactionViewModel.isSolid) {
            var solid = true
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle))) {
                solid = false
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle))) {
                solid = false
            }
            if (solid) {
                transactionViewModel.updateSolid(true)
                transactionViewModel.updateHeights(tangle)
                return true
            }
        }
        //return isSolid();
        return false
    }

    @Throws(Exception::class)
    private fun checkApproovee(approovee: TransactionViewModel): Boolean {
        if (approovee.type == PREFILLED_SLOT) {
            transactionRequester.requestTransaction(approovee.hash, false)
            return false
        }
        return if (approovee.hash == Hash.NULL_HASH) {
            true
        } else approovee.isSolid
    }

    class StaleTimestampException(message: String) : RuntimeException(message)

    companion object {
        private var snapshotTimestamp: Long = 0
        private var snapshotTimestampMs: Long = 0
        private val MAX_TIMESTAMP_FUTURE = (2 * 60 * 60).toLong()
        private val MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1000

        private fun hasInvalidTimestamp(transactionViewModel: TransactionViewModel): Boolean {
            return if (transactionViewModel.attachmentTimestamp == 0L) {
                transactionViewModel.timestamp < snapshotTimestamp && transactionViewModel.hash != Hash.NULL_HASH || transactionViewModel.timestamp > System.currentTimeMillis() / 1000 + MAX_TIMESTAMP_FUTURE
            } else transactionViewModel.attachmentTimestamp < snapshotTimestampMs || transactionViewModel.attachmentTimestamp > System.currentTimeMillis() + MAX_TIMESTAMP_FUTURE_MS
        }

        fun runValidation(transactionViewModel: TransactionViewModel, minWeightMagnitude: Int) {
            transactionViewModel.setMetadata()
            transactionViewModel.setAttachmentData()
            if (hasInvalidTimestamp(transactionViewModel)) {
                throw StaleTimestampException("Invalid transaction timestamp.")
            }
            for (i in VALUE_TRINARY_OFFSET + VALUE_USABLE_TRINARY_SIZE until VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE) {
                if (transactionViewModel.trits()[i] != 0) {
                    throw RuntimeException("Invalid transaction value")
                }
            }

            val weightMagnitude = transactionViewModel.weightMagnitude
            if (weightMagnitude < minWeightMagnitude) {
                throw RuntimeException("Invalid transaction hash")
            }

            if (transactionViewModel.value() != 0L && transactionViewModel.addressHash.trits()[Sponge.HASH_LENGTH - 1] != 0) {
                throw RuntimeException("Invalid transaction address")
            }
        }

        fun validate(trits: IntArray, minWeightMagnitude: Int): TransactionViewModel {
            val transactionViewModel = TransactionViewModel(trits, Hash.calculate(trits, 0, trits.size, SpongeFactory.create(SpongeFactory.Mode.CURLP81)!!))
            runValidation(transactionViewModel, minWeightMagnitude)
            return transactionViewModel
        }

        @JvmOverloads
        fun validate(bytes: ByteArray, minWeightMagnitude: Int, curl: Sponge? = SpongeFactory.create(SpongeFactory.Mode.CURLP81)): TransactionViewModel {
            val transactionViewModel = TransactionViewModel(bytes, Hash.calculate(bytes, TransactionViewModel.TRINARY_SIZE, curl))
            runValidation(transactionViewModel, minWeightMagnitude)
            return transactionViewModel
        }
    }
}
