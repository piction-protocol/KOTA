package com.iota.iri

import com.iota.iri.controllers.*
import com.iota.iri.model.Hash
import com.iota.iri.network.TransactionRequester
import com.iota.iri.zmq.MessageQ
import com.iota.iri.storage.Tangle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.*

/**
 * Created by paul on 4/15/17.
 */
class LedgerValidator(private val tangle: Tangle, private val milestone: Milestone, private val transactionRequester: TransactionRequester, private val messageQ: MessageQ) {

    private val log = LoggerFactory.getLogger(LedgerValidator::class.java)
    @Volatile private var numberOfConfirmedTransactions: Int = 0

    /**
     * Returns a Map of Address and change in balance that can be used to build a new Snapshot state.
     * Under certain conditions, it will return null:
     * - While descending through transactions, if a transaction is marked as {PREFILLED_SLOT}, then its hash has been
     * referenced by some transaction, but the transaction data is not found in the database. It notifies
     * TransactionRequester to increase the probability this transaction will be present the next time this is checked.
     * - When a transaction marked as a tail transaction (if the current index is 0), but it is not the first transaction
     * in any of the BundleValidator's transaction lists, then the bundle is marked as invalid, deleted, and re-requested.
     * - When the bundle is not internally consistent (the sum of all transactions in the bundle must be zero)
     * As transactions are being traversed, it will come upon bundles, and will add the transaction value to {state}.
     * If {milestone} is true, it will search, through trunk and branch, all transactions, starting from {tip},
     * until it reaches a transaction that is marked as a "confirmed" transaction.
     * If {milestone} is false, it will search up until it reaches a confirmed transaction, or until it finds a hash that has been
     * marked as consistent since the previous milestone.
     * @param visitedNonMilestoneSubtangleHashes hashes that have been visited and considered as approved
     * @param tip                                the hash of a transaction to start the search from
     * @param latestSnapshotIndex                index of the latest snapshot to traverse to
     * @param milestone                          marker to indicate whether to stop only at confirmed transactions
     * @return {state}                           the addresses that have a balance changed since the last diff check
     * @throws Exception
     */
    @Throws(Exception::class)
    fun getLatestDiff(visitedNonMilestoneSubtangleHashes: MutableSet<Hash>, tip: Hash?, latestSnapshotIndex: Int, milestone: Boolean): MutableMap<Hash, Long>? {
        val state = HashMap<Hash, Long>()
        var numberOfAnalyzedTransactions = 0
        val countedTx = HashSet(setOf(Hash.NULL_HASH))

        visitedNonMilestoneSubtangleHashes.add(Hash.NULL_HASH)

        val nonAnalyzedTransactions = LinkedList(setOf(tip!!))
        var transactionPointer: Hash
        transactionPointer = nonAnalyzedTransactions.poll()
        while (transactionPointer != null) {
            if (visitedNonMilestoneSubtangleHashes.add(transactionPointer)) {
                val transactionViewModel = TransactionViewModel.fromHash(tangle, transactionPointer)
                if (transactionViewModel.snapshotIndex() == 0 || transactionViewModel.snapshotIndex() > latestSnapshotIndex) {
                    numberOfAnalyzedTransactions++
                    if (transactionViewModel.type == TransactionViewModel.PREFILLED_SLOT) {
                        transactionRequester.requestTransaction(transactionViewModel.hash, milestone)
                        return null
                    } else {
                        if (transactionViewModel.currentIndex == 0L) {

                            var validBundle = false
                            val bundleTransactions = BundleValidator.validate(tangle, transactionViewModel.hash)

                            for (bundleTransactionViewModels in bundleTransactions) {

                                if (BundleValidator.isInconsistent(bundleTransactionViewModels)) {
                                    break
                                }
                                if (bundleTransactionViewModels[0].hash == transactionViewModel.hash) {

                                    validBundle = true

                                    for (bundleTransactionViewModel in bundleTransactionViewModels) {

                                        if (bundleTransactionViewModel.value() != 0L && countedTx.add(bundleTransactionViewModel.hash)) {

                                            val address = bundleTransactionViewModel.addressHash
                                            val value = state[address]
                                            state.put(address, if (value == null)
                                                bundleTransactionViewModel.value()
                                            else
                                                Math.addExact(value, bundleTransactionViewModel.value()))
                                        }
                                    }

                                    break
                                }
                            }
                            if (!validBundle) {
                                return null
                            }
                        }

                        nonAnalyzedTransactions.offer(transactionViewModel.trunkTransactionHash)
                        nonAnalyzedTransactions.offer(transactionViewModel.branchTransactionHash)
                    }
                }
            }
        }

        log.debug("Analyzed transactions = " + numberOfAnalyzedTransactions)
        if (tip == null) {
            numberOfConfirmedTransactions = numberOfAnalyzedTransactions
        }
        log.debug("Confirmed transactions = " + numberOfConfirmedTransactions)
        return state
    }

    /**
     * Descends through the tree of transactions, through trunk and branch, marking each as {mark} until it reaches
     * a transaction while the transaction confirmed marker is mutually exclusive to {mark}
     * // old @param hash start of the update tree
     * @param hash tail to traverse from
     * @param index milestone index
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun updateSnapshotMilestone(hash: Hash, index: Int) {
        val visitedHashes = HashSet<Hash>()
        val nonAnalyzedTransactions = LinkedList(setOf(hash))
        var hashPointer: Hash
        hashPointer = nonAnalyzedTransactions.poll()
        while (hashPointer != null) {
            if (visitedHashes.add(hashPointer)) {
                val transactionViewModel2 = TransactionViewModel.fromHash(tangle, hashPointer)
                if (transactionViewModel2.snapshotIndex() == 0) {
                    transactionViewModel2.setSnapshot(tangle, index)
                    messageQ.publish("%s %s %d sn", transactionViewModel2.addressHash, transactionViewModel2.hash, index)
                    messageQ.publish("sn %d %s %s %s %s %s", index, transactionViewModel2.hash,
                            transactionViewModel2.addressHash,
                            transactionViewModel2.trunkTransactionHash,
                            transactionViewModel2.branchTransactionHash,
                            transactionViewModel2.bundleHash)
                    nonAnalyzedTransactions.offer(transactionViewModel2.trunkTransactionHash)
                    nonAnalyzedTransactions.offer(transactionViewModel2.branchTransactionHash)
                }
            }
        }
    }

    /**
     * Descends through transactions, trunk and branch, beginning at {tip}, until it reaches a transaction marked as
     * confirmed, or until it reaches a transaction that has already been added to the transient consistent set.
     * @param tip
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun updateConsistentHashes(visitedHashes: MutableSet<Hash>, tip: Hash, index: Int) {
        val nonAnalyzedTransactions = LinkedList(setOf(tip))
        var hashPointer: Hash
        hashPointer = nonAnalyzedTransactions.poll()
        while (hashPointer != null) {
            val transactionViewModel2 = TransactionViewModel.fromHash(tangle, hashPointer)
            if (transactionViewModel2.snapshotIndex() == 0 || transactionViewModel2.snapshotIndex() > index) {
                if (visitedHashes.add(hashPointer)) {
                    nonAnalyzedTransactions.offer(transactionViewModel2.trunkTransactionHash)
                    nonAnalyzedTransactions.offer(transactionViewModel2.branchTransactionHash)
                }
            }
        }
    }

    /**
     * Initializes the LedgerValidator. This updates the latest milestone and solid subtangle milestone, and then
     * builds up the confirmed until it reaches the latest consistent confirmed. If any inconsistencies are detected,
     * perhaps by database corruption, it will delete the milestone confirmed and all that follow.
     * It then starts at the earliest consistent milestone index with a confirmed, and analyzes the tangle until it
     * either reaches the latest solid subtangle milestone, or until it reaches an inconsistent milestone.
     * @throws Exception
     */
    @Throws(Exception::class)
    fun init() {
        val latestConsistentMilestone = buildSnapshot()
        if (latestConsistentMilestone != null) {
            log.info("Loaded consistent milestone: #" + latestConsistentMilestone.index()!!)

            milestone.latestSolidSubtangleMilestone = latestConsistentMilestone.hash
            milestone.latestSolidSubtangleMilestoneIndex = latestConsistentMilestone.index()!!
        }
    }

    /**
     * Only called once upon initialization, this builds the {latestSnapshot} state up to the most recent
     * solid milestone confirmed. It gets the earliest confirmed, and while checking for consistency, patches the next
     * newest confirmed diff into its map.
     * @return              the most recent consistent milestone with a confirmed.
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun buildSnapshot(): MilestoneViewModel? {
        var consistentMilestone: MilestoneViewModel? = null
        milestone.latestSnapshot.rwlock.writeLock().lock()
        try {
            var candidateMilestone = MilestoneViewModel.first(tangle)
            while (candidateMilestone != null) {
                if (candidateMilestone.index()!! % 10000 == 0) {
                    val logMessage = StringBuilder()

                    logMessage.append("Building snapshot... Consistent: #")
                    logMessage.append(if (consistentMilestone != null) consistentMilestone.index() else -1)
                    logMessage.append(", Candidate: #")
                    logMessage.append(candidateMilestone.index().toString())

                    log.info(logMessage.toString())
                }
                if (StateDiffViewModel.maybeExists(tangle, candidateMilestone.hash)) {
                    val stateDiffViewModel = StateDiffViewModel.load(tangle, candidateMilestone.hash)

                    if (!stateDiffViewModel.isEmpty) {
                        if (Snapshot.isConsistent(milestone.latestSnapshot.patchedDiff(stateDiffViewModel.diff))) {
                            milestone.latestSnapshot.apply(stateDiffViewModel.diff, candidateMilestone.index()!!)
                            consistentMilestone = candidateMilestone
                        } else {
                            break
                        }
                    }
                }
                candidateMilestone = candidateMilestone.next(tangle)
            }
        } finally {
            milestone.latestSnapshot.rwlock.writeLock().unlock()
        }
        return consistentMilestone
    }

    @Throws(Exception::class)
    fun updateSnapshot(milestoneVM: MilestoneViewModel): Boolean {
        val transactionViewModel = TransactionViewModel.fromHash(tangle, milestoneVM.hash)
        milestone.latestSnapshot.rwlock.writeLock().lock()
        try {
            val transactionSnapshotIndex = transactionViewModel.snapshotIndex()
            var hasSnapshot = transactionSnapshotIndex != 0
            if (!hasSnapshot) {
                val tail = transactionViewModel.hash
                val currentState = getLatestDiff(HashSet(), tail, milestone.latestSnapshot.index(), true)
                hasSnapshot = currentState != null && Snapshot.isConsistent(milestone.latestSnapshot.patchedDiff(currentState))
                if (hasSnapshot) {
                    updateSnapshotMilestone(milestoneVM.hash, milestoneVM.index()!!)
                    val stateDiffViewModel: StateDiffViewModel
                    stateDiffViewModel = StateDiffViewModel(currentState!!, milestoneVM.hash)
                    if (currentState.size != 0) {
                        stateDiffViewModel.store(tangle)
                    }
                    milestone.latestSnapshot.apply(currentState, milestoneVM.index()!!)
                }
            }
            return hasSnapshot
        } finally {
            milestone.latestSnapshot.rwlock.writeLock().unlock()
        }
    }

    @Throws(Exception::class)
    fun checkConsistency(hashes: List<Hash>): Boolean {
        val visitedHashes = HashSet<Hash>()
        val diff = HashMap<Hash, Long>()
        for (hash in hashes) {
            if (!updateDiff(visitedHashes, diff, hash)) {
                return false
            }
        }
        return true
    }

    @Throws(Exception::class)
    fun updateDiff(approvedHashes: MutableSet<Hash>, diff: MutableMap<Hash, Long>, tip: Hash): Boolean {
        if (!TransactionViewModel.fromHash(tangle, tip).isSolid) {
            return false
        }
        if (approvedHashes.contains(tip)) {
            return true
        }
        val visitedHashes = HashSet(approvedHashes)
        val currentState = getLatestDiff(visitedHashes, tip, milestone.latestSnapshot.index(), false) ?: return false
        diff.forEach { key, value ->
            if ((currentState as java.util.Map<Hash, Long>).computeIfPresent(key) { hash, aLong -> value + aLong } == null) {
                (currentState as java.util.Map<Hash, Long>).putIfAbsent(key, value)
            }
        }
        val isConsistent = Snapshot.isConsistent(milestone.latestSnapshot.patchedDiff(currentState))
        if (isConsistent) {
            diff.putAll(currentState)
            approvedHashes.addAll(visitedHashes)
        }
        return isConsistent
    }
}
