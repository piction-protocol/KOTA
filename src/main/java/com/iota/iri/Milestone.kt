package com.iota.iri

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Arrays
import java.util.HashSet
import java.util.concurrent.atomic.AtomicBoolean

import javax.net.ssl.HttpsURLConnection

import com.iota.iri.controllers.*
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.zmq.MessageQ
import com.iota.iri.storage.Tangle
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.iota.iri.hash.ISS
import com.iota.iri.model.Hash
import com.iota.iri.utils.Converter

import com.iota.iri.Milestone.Validity.*

class Milestone(private val tangle: Tangle,
                private val coordinator: Hash,
                var latestSnapshot: Snapshot,
                private val transactionValidator: TransactionValidator,
                private val testnet: Boolean,
                private val messageQ: MessageQ,
                private val numOfKeysInMilestone: Int,
                val milestoneStartIndex: Int,
                private val acceptAnyTestnetCoo: Boolean
) {

    private val log = LoggerFactory.getLogger(Milestone::class.java)

    private var ledgerValidator: LedgerValidator? = null
    var latestMilestone = Hash.NULL_HASH
    var latestSolidSubtangleMilestone = latestMilestone

    var latestMilestoneIndex: Int = 0
    var latestSolidSubtangleMilestoneIndex: Int = 0

    private val analyzedMilestoneCandidates = HashSet<Hash>()

    private var shuttingDown: Boolean = false

    internal enum class Validity {
        VALID,
        INVALID,
        INCOMPLETE
    }

    init {
        this.latestMilestoneIndex = milestoneStartIndex
        this.latestSolidSubtangleMilestoneIndex = milestoneStartIndex
    }

    @Throws(Exception::class)
    fun init(mode: SpongeFactory.Mode, ledgerValidator: LedgerValidator, revalidate: Boolean) {
        this.ledgerValidator = ledgerValidator
        val ledgerValidatorInitialized = AtomicBoolean(false)
        Thread({
            log.info("Waiting for Ledger Validator initialization...")
            while (!ledgerValidatorInitialized.get()) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                }

            }
            log.info("Tracker started.")
            while (!shuttingDown) {
                val scanTime = System.currentTimeMillis()

                try {
                    val previousLatestMilestoneIndex = latestMilestoneIndex
                    val hashes = AddressViewModel.load(tangle, coordinator).getHashes()
                    run {
                        // Update Milestone
                        run {
                            // find new milestones
                            for (hash in hashes) {
                                if (analyzedMilestoneCandidates.add(hash)) {
                                    val t = TransactionViewModel.fromHash(tangle, hash)
                                    if (t.currentIndex == 0L) {
                                        val valid = validateMilestone(mode, t, getIndex(t))
                                        when (valid) {
                                            VALID -> {
                                                val milestoneViewModel = MilestoneViewModel.latest(tangle)
                                                if (milestoneViewModel != null && milestoneViewModel.index()!! > latestMilestoneIndex) {
                                                    latestMilestone = milestoneViewModel.hash
                                                    latestMilestoneIndex = milestoneViewModel.index()!!
                                                }
                                            }
                                            INCOMPLETE -> analyzedMilestoneCandidates.remove(t.hash)
                                            INVALID -> {
                                            }
                                        }//Do nothing
                                    }
                                }
                            }
                        }
                    }

                    if (previousLatestMilestoneIndex != latestMilestoneIndex) {
                        messageQ.publish("lmi %d %d", previousLatestMilestoneIndex, latestMilestoneIndex)
                        log.info("Latest milestone has changed from #" + previousLatestMilestoneIndex
                                + " to #" + latestMilestoneIndex)
                    }

                    Thread.sleep(Math.max(1, RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime)))
                } catch (e: Exception) {
                    log.error("Error during Latest Milestone updating", e)
                }

            }
        }, "Latest Milestone Tracker").start()

        Thread({
            log.info("Initializing Ledger Validator...")
            try {
                ledgerValidator.init()
                ledgerValidatorInitialized.set(true)
            } catch (e: Exception) {
                log.error("Error initializing snapshots. Skipping.", e)
            }

            log.info("Tracker started.")
            while (!shuttingDown) {
                val scanTime = System.currentTimeMillis()

                try {
                    val previousSolidSubtangleLatestMilestoneIndex = latestSolidSubtangleMilestoneIndex

                    if (latestSolidSubtangleMilestoneIndex < latestMilestoneIndex) {
                        updateLatestSolidSubtangleMilestone()
                    }

                    if (previousSolidSubtangleLatestMilestoneIndex != latestSolidSubtangleMilestoneIndex) {

                        messageQ.publish("lmsi %d %d", previousSolidSubtangleLatestMilestoneIndex, latestSolidSubtangleMilestoneIndex)
                        messageQ.publish("lmhs %s", latestSolidSubtangleMilestone)
                        log.info("Latest SOLID SUBTANGLE milestone has changed from #"
                                + previousSolidSubtangleLatestMilestoneIndex + " to #"
                                + latestSolidSubtangleMilestoneIndex)
                    }

                    Thread.sleep(Math.max(1, RESCAN_INTERVAL - (System.currentTimeMillis() - scanTime)))

                } catch (e: Exception) {
                    log.error("Error during Solid Milestone updating", e)
                }

            }
        }, "Solid Milestone Tracker").start()


    }

    @Throws(Exception::class)
    private fun validateMilestone(mode: SpongeFactory.Mode, transactionViewModel: TransactionViewModel, index: Int): Validity {
        if (index < 0 || index >= 0x200000) {
            return INVALID
        }

        if (MilestoneViewModel[tangle, index] != null) {
            // Already validated.
            return VALID
        }
        val bundleTransactions = BundleValidator.validate(tangle, transactionViewModel.hash)
        if (bundleTransactions.size == 0) {
            return INCOMPLETE
        } else {
            for (bundleTransactionViewModels in bundleTransactions) {

                //if (Arrays.equals(bundleTransactionViewModels.get(0).getHash(),transactionViewModel.getHash())) {
                if (bundleTransactionViewModels[0].hash == transactionViewModel.hash) {

                    //final TransactionViewModel transactionViewModel2 = StorageTransactions.instance().loadTransaction(transactionViewModel.trunkTransactionPointer);
                    val transactionViewModel2 = transactionViewModel.getTrunkTransaction(tangle)
                    if (transactionViewModel2.type == TransactionViewModel.FILLED_SLOT
                            && transactionViewModel.branchTransactionHash == transactionViewModel2.trunkTransactionHash
                            && transactionViewModel.bundleHash == transactionViewModel2.bundleHash) {

                        val trunkTransactionTrits = transactionViewModel.trunkTransactionHash.trits()
                        val signatureFragmentTrits = Arrays.copyOfRange(transactionViewModel.trits(), TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE)

                        val merkleRoot = ISS.getMerkleRoot(mode, ISS.address(mode, ISS.digest(mode,
                                Arrays.copyOf(ISS.normalizedBundle(trunkTransactionTrits),
                                        ISS.NUMBER_OF_FRAGMENT_CHUNKS),
                                signatureFragmentTrits)),
                                transactionViewModel2.trits(), 0, index, numOfKeysInMilestone)
                        if (testnet && acceptAnyTestnetCoo || Hash(merkleRoot) == coordinator) {
                            MilestoneViewModel(index, transactionViewModel.hash).store(tangle)
                            return VALID
                        } else {
                            return INVALID
                        }
                    }
                }
            }
        }
        return INVALID
    }

    @Throws(Exception::class)
    internal fun updateLatestSolidSubtangleMilestone() {
        var milestoneViewModel: MilestoneViewModel?
        val latest = MilestoneViewModel.latest(tangle)
        if (latest != null) {
            milestoneViewModel = MilestoneViewModel.findClosestNextMilestone(
                    tangle, latestSolidSubtangleMilestoneIndex, testnet, milestoneStartIndex)
            while (milestoneViewModel != null && milestoneViewModel.index()!! <= latest.index()!! && !shuttingDown) {
                if (transactionValidator.checkSolidity(milestoneViewModel.hash, true) &&
                        milestoneViewModel.index()!! >= latestSolidSubtangleMilestoneIndex &&
                        ledgerValidator!!.updateSnapshot(milestoneViewModel)) {
                    latestSolidSubtangleMilestone = milestoneViewModel.hash
                    latestSolidSubtangleMilestoneIndex = milestoneViewModel.index()!!
                } else {
                    break
                }
                milestoneViewModel = milestoneViewModel.next(tangle)
            }
        }
    }

    internal fun shutDown() {
        shuttingDown = true
    }

    fun reportToSlack(milestoneIndex: Int, depth: Int, nextDepth: Int) {
        try {
            val request = "token=" + URLEncoder.encode("<botToken>", "UTF-8") + "&channel=" + URLEncoder.encode("#botbox", "UTF-8") + "&text=" + URLEncoder.encode("TESTNET: ", "UTF-8") + "&as_user=true"
            val connection = URL("https://slack.com/api/chat.postMessage").openConnection() as HttpsURLConnection
            connection.setHostnameVerifier { _, _ -> true }
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.requestMethod = "POST"
            connection.doOutput = true
            val out = connection.outputStream
            out.write(request.toByteArray(charset("UTF-8")))
            out.close()
            val result = ByteArrayOutputStream()
            val inputStream = connection.inputStream
            val buffer = ByteArray(1024)
            var length = inputStream.read(buffer)
            while (length != -1) {
                result.write(buffer, 0, length)
            }
            log.info(result.toString("UTF-8"))

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private val RESCAN_INTERVAL = 5000

        internal fun getIndex(transactionViewModel: TransactionViewModel): Int {
            return Converter.longValue(transactionViewModel.trits(), TransactionViewModel.OBSOLETE_TAG_TRINARY_OFFSET, 15).toInt()
        }
    }
}
