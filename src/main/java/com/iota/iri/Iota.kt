package com.iota.iri

import com.iota.iri.conf.Configuration
import com.iota.iri.controllers.TipsViewModel
import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.network.Node
import com.iota.iri.network.TransactionRequester
import com.iota.iri.network.UDPReceiver
import com.iota.iri.network.replicator.Replicator
import com.iota.iri.service.TipsManager
import com.iota.iri.storage.FileExportProvider
import com.iota.iri.storage.Tangle
import com.iota.iri.storage.ZmqPublishProvider
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import com.iota.iri.zmq.MessageQ
import org.apache.commons.lang3.NotImplementedException
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Created by paul on 5/19/17.
 */
class Iota @Throws(IOException::class)
constructor(val configuration: Configuration) {

    val ledgerValidator: LedgerValidator
    val milestone: Milestone
    val tangle: Tangle
    val transactionValidator: TransactionValidator
    val tipsManager: TipsManager
    val transactionRequester: TransactionRequester
    val node: Node
    val udpReceiver: UDPReceiver
    val replicator: Replicator
    val coordinator: Hash
    val tipsViewModel: TipsViewModel
    val messageQ: MessageQ

    val testnet: Boolean
    val maxPeers: Int
    val udpPort: Int
    val tcpPort: Int
    val maxTipSearchDepth: Int

    init {
        testnet = configuration.booling(Configuration.DefaultConfSettings.TESTNET)
        maxPeers = configuration.integer(Configuration.DefaultConfSettings.MAX_PEERS)
        udpPort = configuration.integer(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT)
        tcpPort = configuration.integer(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT)

        val snapshotFile = configuration.string(Configuration.DefaultConfSettings.SNAPSHOT_FILE)
        val snapshotSigFile = configuration.string(Configuration.DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE)
        val initialSnapshot = Snapshot.init(snapshotFile, snapshotSigFile, testnet).clone()
        val snapshotTimestamp = configuration.longNum(Configuration.DefaultConfSettings.SNAPSHOT_TIME)
        val milestoneStartIndex = configuration.integer(Configuration.DefaultConfSettings.MILESTONE_START_INDEX)
        val numKeysMilestone = configuration.integer(Configuration.DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE)
        val dontValidateMilestoneSig = configuration.booling(Configuration.DefaultConfSettings
                .DONT_VALIDATE_TESTNET_MILESTONE_SIG)
        val transactionPacketSize = configuration.integer(Configuration.DefaultConfSettings.TRANSACTION_PACKET_SIZE)

        maxTipSearchDepth = configuration.integer(Configuration.DefaultConfSettings.MAX_DEPTH)
        if (testnet) {
            val coordinatorTrytes = configuration.string(Configuration.DefaultConfSettings.COORDINATOR)
            if (StringUtils.isNotEmpty(coordinatorTrytes)) {
                coordinator = Hash(coordinatorTrytes)
            } else {
                log.warn("No coordinator address given for testnet. Defaulting to " + Configuration.TESTNET_COORDINATOR_ADDRESS)
                coordinator = Hash(Configuration.TESTNET_COORDINATOR_ADDRESS)
            }
        } else {
            coordinator = Hash(Configuration.MAINNET_COORDINATOR_ADDRESS)
        }
        tangle = Tangle()
        messageQ = MessageQ(configuration.integer(Configuration.DefaultConfSettings.ZMQ_PORT),
                configuration.string(Configuration.DefaultConfSettings.ZMQ_IPC),
                configuration.integer(Configuration.DefaultConfSettings.ZMQ_THREADS),
                configuration.booling(Configuration.DefaultConfSettings.ZMQ_ENABLED)
        )
        tipsViewModel = TipsViewModel()
        transactionRequester = TransactionRequester(tangle, messageQ)
        transactionValidator = TransactionValidator(tangle, tipsViewModel, transactionRequester, messageQ,
                snapshotTimestamp)
        milestone = Milestone(tangle, coordinator, initialSnapshot, transactionValidator, testnet, messageQ,
                numKeysMilestone, milestoneStartIndex, dontValidateMilestoneSig)
        node = Node(configuration, tangle, transactionValidator, transactionRequester, tipsViewModel, milestone, messageQ)
        replicator = Replicator(node, tcpPort, maxPeers, testnet, transactionPacketSize)
        udpReceiver = UDPReceiver(udpPort, node, configuration.integer(Configuration.DefaultConfSettings.TRANSACTION_PACKET_SIZE))
        ledgerValidator = LedgerValidator(tangle, milestone, transactionRequester, messageQ)
        tipsManager = TipsManager(tangle, ledgerValidator, transactionValidator, tipsViewModel, milestone,
                maxTipSearchDepth, messageQ, testnet, milestoneStartIndex)
    }

    @Throws(Exception::class)
    fun init() {
        initializeTangle()
        tangle.init()

        if (configuration.booling(Configuration.DefaultConfSettings.RESCAN_DB)) {
            rescan_db()
        }
        val revalidate = configuration.booling(Configuration.DefaultConfSettings.REVALIDATE)

        if (revalidate) {
            tangle.clearColumn(com.iota.iri.model.Milestone::class.java)
            tangle.clearColumn(com.iota.iri.model.StateDiff::class.java)
            tangle.clearMetadata(com.iota.iri.model.Transaction::class.java)
        }
        milestone.init(SpongeFactory.Mode.CURLP27, ledgerValidator, revalidate)
        transactionValidator.init(testnet, configuration.integer(Configuration.DefaultConfSettings.MWM))
        tipsManager.init()
        transactionRequester.init(configuration.doubling(Configuration.DefaultConfSettings.P_REMOVE_REQUEST.name))
        udpReceiver.init()
        replicator.init()
        node.init()
    }

    @Throws(Exception::class)
    private fun rescan_db() {
        //delete all transaction indexes
        tangle.clearColumn(com.iota.iri.model.Address::class.java)
        tangle.clearColumn(com.iota.iri.model.Bundle::class.java)
        tangle.clearColumn(com.iota.iri.model.Approvee::class.java)
        tangle.clearColumn(com.iota.iri.model.ObsoleteTag::class.java)
        tangle.clearColumn(com.iota.iri.model.Tag::class.java)
        tangle.clearColumn(com.iota.iri.model.Milestone::class.java)
        tangle.clearColumn(com.iota.iri.model.StateDiff::class.java)
        tangle.clearMetadata(com.iota.iri.model.Transaction::class.java)

        //rescan all tx & refill the columns
        var tx = TransactionViewModel.first(tangle)
        var counter = 0
        while (tx != null) {
            if (++counter % 10000 == 0) {
                log.info("Rescanned {} Transactions", counter)
            }
            val saveBatch = tx.saveBatch
            saveBatch.removeAt(5)
            tangle.saveBatch(saveBatch)
            tx = tx.next(tangle)
        }
    }

    @Throws(Exception::class)
    fun shutdown() {
        milestone.shutDown()
        tipsManager.shutdown()
        node.shutdown()
        udpReceiver.shutdown()
        replicator.shutdown()
        transactionValidator.shutdown()
        tangle.shutdown()
        messageQ.shutdown()
    }

    private fun initializeTangle() {
        val dbPath = configuration.string(Configuration.DefaultConfSettings.DB_PATH)
        if (testnet) {
            if (dbPath.isEmpty() || dbPath == "mainnetdb") {
                // testnetusers must not use mainnetdb, overwrite it unless an explicit name is set.
                configuration.put(Configuration.DefaultConfSettings.DB_PATH.name, "testnetdb")
                configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name, "testnetdb.log")
            }
        } else {
            if (dbPath.isEmpty() || dbPath == "testnetdb") {
                // mainnetusers must not use testnetdb, overwrite it unless an explicit name is set.
                configuration.put(Configuration.DefaultConfSettings.DB_PATH.name, "mainnetdb")
                configuration.put(Configuration.DefaultConfSettings.DB_LOG_PATH.name, "mainnetdb.log")
            }
        }
        when (configuration.string(Configuration.DefaultConfSettings.MAIN_DB)) {
            "rocksdb" -> {
                tangle.addPersistenceProvider(RocksDBPersistenceProvider(
                        configuration.string(Configuration.DefaultConfSettings.DB_PATH),
                        configuration.string(Configuration.DefaultConfSettings.DB_LOG_PATH),
                        configuration.integer(Configuration.DefaultConfSettings.DB_CACHE_SIZE)))
            }
            else -> {
                throw NotImplementedException("No such database type.")
            }
        }
        if (configuration.booling(Configuration.DefaultConfSettings.EXPORT)) {
            tangle.addPersistenceProvider(FileExportProvider())
        }
        if (configuration.booling(Configuration.DefaultConfSettings.ZMQ_ENABLED)) {
            tangle.addPersistenceProvider(ZmqPublishProvider(messageQ))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(Iota::class.java)
    }
}
