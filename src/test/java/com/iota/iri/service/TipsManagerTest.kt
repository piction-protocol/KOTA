package com.iota.iri.service

import com.iota.iri.LedgerValidator
import com.iota.iri.Milestone
import com.iota.iri.Snapshot
import com.iota.iri.TransactionValidator
import com.iota.iri.conf.Configuration
import com.iota.iri.controllers.TipsViewModel
import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.controllers.TransactionViewModelTest
import com.iota.iri.model.Hash
import com.iota.iri.network.TransactionRequester
import com.iota.iri.storage.Tangle
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import com.iota.iri.zmq.MessageQ
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.util.*

/**
 * Created by paul on 4/27/17.
 */
class TipsManagerTest {

    @Test
    @Throws(Exception::class)
    fun capSum() {
        var a: Long = 0
        var b: Long
        val max = java.lang.Long.MAX_VALUE / 2
        b = 0
        while (b < max) {
            a = TipsManager.capSum(a, b, max)
            Assert.assertTrue("a should never go above max", a <= max)
            b += max / 100
        }
    }

    @Test
    @Throws(Exception::class)
    fun updateLinearRatingsTestWorks() {
        val transaction: TransactionViewModel
        val transaction1: TransactionViewModel
        val transaction2: TransactionViewModel
        transaction = TransactionViewModel(TransactionViewModelTest.randomTransactionTrits, TransactionViewModelTest.randomTransactionHash)
        transaction1 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction.hash, transaction.hash), TransactionViewModelTest.randomTransactionHash)
        transaction2 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction1.hash, transaction1.hash), TransactionViewModelTest.randomTransactionHash)
        transaction.store(tangle)
        transaction1.store(tangle)
        transaction2.store(tangle)
        val ratings = HashMap<Hash, Set<Hash>>()
        tipsManager!!.updateHashRatings(transaction.hash, ratings, HashSet())
        Assert.assertEquals(ratings[transaction.hash]?.size?.toLong(), 3)
        Assert.assertEquals(ratings[transaction1.hash]?.size?.toLong(), 2)
        Assert.assertEquals(ratings[transaction2.hash]?.size?.toLong(), 1)
    }

    @Test
    @Throws(Exception::class)
    fun updateRatingsTestWorks() {
        val transaction: TransactionViewModel
        val transaction1: TransactionViewModel
        val transaction2: TransactionViewModel
        val transaction3: TransactionViewModel
        val transaction4: TransactionViewModel
        transaction = TransactionViewModel(TransactionViewModelTest.randomTransactionTrits, TransactionViewModelTest.randomTransactionHash)
        transaction1 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction.hash, transaction.hash), TransactionViewModelTest.randomTransactionHash)
        transaction2 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction1.hash, transaction1.hash), TransactionViewModelTest.randomTransactionHash)
        transaction3 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction2.hash, transaction1.hash), TransactionViewModelTest.randomTransactionHash)
        transaction4 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction2.hash, transaction3.hash), TransactionViewModelTest.randomTransactionHash)
        transaction.store(tangle)
        transaction1.store(tangle)
        transaction2.store(tangle)
        transaction3.store(tangle)
        transaction4.store(tangle)
        val ratings = HashMap<Hash, Set<Hash>>()
        tipsManager!!.updateHashRatings(transaction.hash, ratings, HashSet())
        Assert.assertEquals(ratings[transaction.hash]?.size?.toLong(), 5)
        Assert.assertEquals(ratings[transaction1.hash]?.size?.toLong(), 4)
        Assert.assertEquals(ratings[transaction2.hash]?.size?.toLong(), 3)
    }

    @Test
    @Throws(Exception::class)
    fun updateRatings2TestWorks() {
        val transaction: TransactionViewModel
        val transaction1: TransactionViewModel
        val transaction2: TransactionViewModel
        val transaction3: TransactionViewModel
        val transaction4: TransactionViewModel
        transaction = TransactionViewModel(TransactionViewModelTest.randomTransactionTrits, TransactionViewModelTest.randomTransactionHash)
        transaction1 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction.hash, transaction.hash), TransactionViewModelTest.randomTransactionHash)
        transaction2 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction1.hash, transaction1.hash), TransactionViewModelTest.randomTransactionHash)
        transaction3 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction2.hash, transaction2.hash), TransactionViewModelTest.randomTransactionHash)
        transaction4 = TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(transaction3.hash, transaction3.hash), TransactionViewModelTest.randomTransactionHash)
        transaction.store(tangle)
        transaction1.store(tangle)
        transaction2.store(tangle)
        transaction3.store(tangle)
        transaction4.store(tangle)
        val ratings = HashMap<Hash, Long>()
        tipsManager!!.recursiveUpdateRatings(transaction.hash, ratings, HashSet())
        Assert.assertTrue(ratings[transaction.hash] == 5L)
    }

    @Test
    @Throws(Exception::class)
    fun updateRatingsSerialWorks() {
        val hashes = arrayOfNulls<Hash>(5)
        hashes[0] = TransactionViewModelTest.randomTransactionHash
        TransactionViewModel(TransactionViewModelTest.randomTransactionTrits, hashes[0]).store(tangle)
        for (i in 1 until hashes.size) {
            hashes[i] = TransactionViewModelTest.randomTransactionHash
            TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hashes[i - 1]!!, hashes[i - 1]!!), hashes[i]).store(tangle)
        }
        val ratings = HashMap<Hash, Long>()
        tipsManager!!.recursiveUpdateRatings(hashes[0], ratings, HashSet())
        Assert.assertTrue(ratings[hashes[0]] == 5L)
    }

    @Test
    @Throws(Exception::class)
    fun updateRatingsSerialWorks2() {
        val hashes = arrayOfNulls<Hash>(5)
        hashes[0] = TransactionViewModelTest.randomTransactionHash
        TransactionViewModel(TransactionViewModelTest.randomTransactionTrits, hashes[0]).store(tangle)
        for (i in 1 until hashes.size) {
            hashes[i] = TransactionViewModelTest.randomTransactionHash
            TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hashes[i - 1]!!, hashes[i - (if (i > 1) 2 else 1)]!!), hashes[i]).store(tangle)
        }
        val ratings = HashMap<Hash, Long>()
        tipsManager!!.recursiveUpdateRatings(hashes[0], ratings, HashSet())
        Assert.assertTrue(ratings[hashes[0]] == 12L)
    }

    //@Test
    @Throws(Exception::class)
    fun testUpdateRatingsTime() {
        val max = 100001
        var time: Long
        val times = LinkedList<Long>()
        var size = 1
        while (size < max) {
            time = ratingTime(size)
            times.add(time)
            size *= 10
        }
        Assert.assertEquals(1, 1)
    }

    @Throws(Exception::class)
    fun ratingTime(size: Int): Long {
        val hashes = arrayOfNulls<Hash>(size)
        hashes[0] = TransactionViewModelTest.randomTransactionHash
        TransactionViewModel(TransactionViewModelTest.randomTransactionTrits, hashes[0]).store(tangle)
        val random = Random()
        for (i in 1 until hashes.size) {
            hashes[i] = TransactionViewModelTest.randomTransactionHash
            TransactionViewModel(TransactionViewModelTest.getRandomTransactionWithTrunkAndBranch(hashes[i - random.nextInt(i) - 1]!!, hashes[i - random.nextInt(i) - 1]!!), hashes[i]).store(tangle)
        }
        val ratings = HashMap<Hash, Long>()
        val start = System.currentTimeMillis()
        tipsManager!!.serialUpdateRatings(HashSet(), hashes[0], ratings, HashSet(), null)
        return System.currentTimeMillis() - start
    }

    companion object {

        private val dbFolder = TemporaryFolder()
        private val logFolder = TemporaryFolder()
        private var tangle: Tangle? = null
        private var tipsManager: TipsManager? = null

        @AfterClass
        @Throws(Exception::class)
        fun tearDown() {
            tangle!!.shutdown()
            dbFolder.delete()
        }

        @BeforeClass
        @Throws(Exception::class)
        fun setUp() {
            tangle = Tangle()
            dbFolder.create()
            logFolder.create()
            tangle!!.addPersistenceProvider(RocksDBPersistenceProvider(dbFolder.root.absolutePath, logFolder.root.absolutePath, 1000))
            tangle!!.init()
            val tipsViewModel = TipsViewModel()
            val messageQ = MessageQ(0, null, 1, false)
            val transactionRequester = TransactionRequester(tangle, messageQ)
            val transactionValidator = TransactionValidator(tangle!!, tipsViewModel, transactionRequester,
                    messageQ, java.lang.Long.parseLong(Configuration.GLOBAL_SNAPSHOT_TIME))
            val milestoneStartIndex = Integer.parseInt(Configuration.MAINNET_MILESTONE_START_INDEX)
            val numOfKeysInMilestone = Integer.parseInt(Configuration.MAINNET_NUM_KEYS_IN_MILESTONE)
            val milestone = Milestone(tangle!!, Hash.NULL_HASH, Snapshot.init(
                    Configuration.MAINNET_SNAPSHOT_FILE, Configuration.MAINNET_SNAPSHOT_SIG_FILE, false).clone(),
                    transactionValidator, false, messageQ, numOfKeysInMilestone,
                    milestoneStartIndex, true)
            val ledgerValidator = LedgerValidator(tangle!!, milestone, transactionRequester, messageQ)
            tipsManager = TipsManager(tangle, ledgerValidator, transactionValidator, tipsViewModel, milestone,
                    15, messageQ, false, milestoneStartIndex)
        }
    }
}