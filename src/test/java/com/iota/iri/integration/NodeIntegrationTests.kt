package com.iota.iri.integration

import com.iota.iri.IXI
import com.iota.iri.Iota
import com.iota.iri.compose.IntegerArrayFunction
import com.iota.iri.conf.Configuration

import com.iota.iri.controllers.TransactionViewModel.*
import com.iota.iri.hash.Curl
import com.iota.iri.hash.Sponge
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.network.Node
import com.iota.iri.service.API
import com.iota.iri.utils.Converter
import org.apache.commons.lang3.ArrayUtils
import org.junit.After
import org.junit.Before
import org.junit.rules.TemporaryFolder

import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors

/**
 * Created by paul on 5/19/17.
 */
class NodeIntegrationTests {

    internal val waitObj = Object()
    internal var shutdown = AtomicBoolean(false)

    @Before
    @Throws(Exception::class)
    fun setUp() {
        shutdown.set(false)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
    }


    //@Test
    @Throws(Exception::class)
    fun testGetsSolid() {
        val count = 1
        val spacing: Long = 5000
        val iotaNodes = arrayOfNulls<Iota>(count)
        val api = arrayOfNulls<API>(count)
        val ixi = arrayOfNulls<IXI>(count)
        val cooThread: Thread
        val master: Thread
        val folders = arrayOfNulls<TemporaryFolder>(count * 2)
        for (i in 0 until count) {
            folders[i * 2] = TemporaryFolder()
            folders[i * 2 + 1] = TemporaryFolder()
            iotaNodes[i] = newNode(i, folders[i * 2]!!, folders[i * 2 + 1]!!)
            ixi[i] = IXI(iotaNodes[i]!!)
            ixi[i]?.init(iotaNodes[i]!!.configuration.string(Configuration.DefaultConfSettings.IXI_DIR))
            api[i] = API(iotaNodes[i], ixi[i])
            api[i]?.init()
        }
        Node.uri("udp://localhost:14701").ifPresent { uri -> iotaNodes[0]?.node?.addNeighbor(iotaNodes[0]?.node!!.newNeighbor(uri, true)) }
        //Node.uri("udp://localhost:14700").ifPresent(uri -> iotaNodes[1].node.addNeighbor(iotaNodes[1].node.newNeighbor(uri, true)));

        cooThread = Thread(spawnCoordinator(api[0]!!, spacing), "Coordinator")
        master = Thread(spawnMaster(), "master")
        /*
        TODO: Put some test stuff here
         */
        cooThread.start()
        master.start()

        synchronized(waitObj) {
            waitObj.wait()
        }
        for (i in 0 until count) {
            ixi[i]?.shutdown()
            api[i]?.shutDown()
            iotaNodes[i]?.shutdown()
        }
        for (folder in folders) {
            folder?.delete()
        }
    }

    @Throws(Exception::class)
    private fun newNode(index: Int, db: TemporaryFolder, log: TemporaryFolder): Iota {
        db.create()
        log.create()
        val conf = Configuration()
        val iota: Iota
        conf.put(Configuration.DefaultConfSettings.PORT, (14800 + index).toString())
        conf.put(Configuration.DefaultConfSettings.UDP_RECEIVER_PORT, (14700 + index).toString())
        conf.put(Configuration.DefaultConfSettings.TCP_RECEIVER_PORT, (14700 + index).toString())
        conf.put(Configuration.DefaultConfSettings.DB_PATH, db.root.absolutePath)
        conf.put(Configuration.DefaultConfSettings.DB_LOG_PATH, log.root.absolutePath)
        conf.put(Configuration.DefaultConfSettings.TESTNET, "true")
        iota = Iota(conf)
        iota.init()
        return iota
    }

    private fun spawnMaster(): Runnable {
        return Runnable {
            try {
                Thread.sleep(20000)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            shutdown.set(true)
            synchronized(waitObj) {
                waitObj.notifyAll()
            }
        }
    }

    internal fun spawnCoordinator(api: API, spacing: Long): Runnable {
        return Runnable {
            var index: Long = 0
            try {
                newMilestone(api, arrayOf(Hash.NULL_HASH, Hash.NULL_HASH), index++)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            while (!shutdown.get()) {
                try {
                    Thread.sleep(spacing)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                try {
                    sendMilestone(api, index++)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        }
    }

    @Throws(Exception::class)
    private fun sendMilestone(api: API, index: Long) {
        newMilestone(api, api.getTransactionToApproveStatement(10, null, 1)!!, index)
    }

    @Throws(Exception::class)
    private fun newMilestone(api: API, tips: Array<Hash>, index: Long) {
        val transactions = ArrayList<IntArray>()
        transactions.add(IntArray(TRINARY_SIZE))
        Converter.copyTrits(index, transactions[0], OBSOLETE_TAG_TRINARY_OFFSET, OBSOLETE_TAG_TRINARY_SIZE)
        transactions.add(Arrays.copyOf(transactions[0], TRINARY_SIZE))
        val coordinator = Hash(Configuration.TESTNET_COORDINATOR_ADDRESS)
        System.arraycopy(coordinator.trits(), 0, transactions[0], ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_SIZE)
        setBundleHash(transactions, null)
        /*val elements = api.attachToTangleStatement(tips[0], tips[1], 13, transactions.stream().map<String>(IntegerArrayFunction()).collect<MutableList<String>, Any>(Collectors.toList()))
        api.storeTransactionStatement(elements)
        api.broadcastTransactionStatement(elements)*/
    }

    fun setBundleHash(transactions: List<IntArray>, customCurl: Curl?) {

        val hash = IntArray(Sponge.HASH_LENGTH)

        val curl = customCurl ?: SpongeFactory.create(SpongeFactory.Mode.CURLP81)
        curl!!.reset()

        for (i in transactions.indices) {
            var t = Arrays.copyOfRange(transactions[i], ADDRESS_TRINARY_OFFSET, ADDRESS_TRINARY_OFFSET + ADDRESS_TRINARY_SIZE)

            val valueTrits = Arrays.copyOfRange(transactions[i], VALUE_TRINARY_OFFSET, VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE)
            t = ArrayUtils.addAll(t, *valueTrits)

            val tagTrits = Arrays.copyOfRange(transactions[i], OBSOLETE_TAG_TRINARY_OFFSET, OBSOLETE_TAG_TRINARY_OFFSET + OBSOLETE_TAG_TRINARY_SIZE)
            t = ArrayUtils.addAll(t, *tagTrits)

            val timestampTrits = Arrays.copyOfRange(transactions[i], TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_OFFSET + TIMESTAMP_TRINARY_SIZE)
            t = ArrayUtils.addAll(t, *timestampTrits)

            Converter.copyTrits(i.toLong(), transactions[i], CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE)
            val currentIndexTrits = Arrays.copyOfRange(transactions[i], CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_OFFSET + CURRENT_INDEX_TRINARY_SIZE)
            t = ArrayUtils.addAll(t, *currentIndexTrits)

            Converter.copyTrits(transactions.size.toLong(), transactions[i], LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE)
            val lastIndexTrits = Arrays.copyOfRange(transactions[i], LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_OFFSET + LAST_INDEX_TRINARY_SIZE)
            t = ArrayUtils.addAll(t, *lastIndexTrits)

            curl.absorb(t, 0, t.size)
        }

        curl.squeeze(hash, 0, hash.size)

        for (i in transactions.indices) {
            System.arraycopy(hash, 0, transactions[i], BUNDLE_TRINARY_OFFSET, BUNDLE_TRINARY_SIZE)
        }
    }

}
