package com.iota.iri

import com.iota.iri.conf.Configuration
import com.iota.iri.controllers.TipsViewModel
import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.controllers.TransactionViewModelTest
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.network.TransactionRequester
import com.iota.iri.storage.Tangle
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import com.iota.iri.utils.Converter
import com.iota.iri.zmq.MessageQ
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Created by paul on 5/14/17.  */
class TransactionValidatorTest {

    private val txWithBranchAndTrunk: TransactionViewModel
        @Throws(Exception::class)
        get() {
            val tx: TransactionViewModel
            val trunkTx: TransactionViewModel
            val branchTx: TransactionViewModel
            val trytes = "999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999CFDEZBLZQYA9999999999999999999999999999999999999999999ZZWQHWD99C99999999C99999999CKWWDBWSCLMQULCTAAJGXDEMFJXPMGMAQIHDGHRBGEMUYNNCOK9YPHKEEFLFCZUSPMCJHAKLCIBQSGWAS999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999999"

            val trits = Converter.allocateTritsForTrytes(trytes.length)
            Converter.trits(trytes, trits, 0)
            trunkTx = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))
            branchTx = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))

            val childTx = TransactionViewModelTest.randomTransactionTrits
            System.arraycopy(trunkTx.hash.trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE)
            System.arraycopy(branchTx.hash.trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE)
            tx = TransactionViewModel(childTx, Hash.calculate(SpongeFactory.Mode.CURLP81, childTx))

            trunkTx.store(tangle)
            branchTx.store(tangle)
            tx.store(tangle)

            return tx
        }

    private val txWithoutBranchAndTrunk: TransactionViewModel
        @Throws(Exception::class)
        get() {
            val trits = TransactionViewModelTest.randomTransactionTrits
            val tx = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))

            tx.store(tangle)

            return tx
        }

    @Test
    @Throws(InterruptedException::class)
    fun testMinMwm() {
        txValidator!!.shutdown()
        txValidator!!.init(false, 5)
        assertTrue(txValidator!!.minWeightMagnitude == 13)
        txValidator!!.shutdown()
        txValidator!!.init(false, MAINNET_MWM)
    }

    @Test
    @Throws(Exception::class)
    fun validateBytes() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        Converter.copyTrits(0, trits, 0, trits.size)
        val bytes = Converter.allocateBytesForTrits(trits.size)
        Converter.bytes(trits, bytes)
        TransactionValidator.validate(bytes, MAINNET_MWM)
    }

    @Test
    fun validateTrits() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        Converter.copyTrits(0, trits, 0, trits.size)
        TransactionValidator.validate(trits, MAINNET_MWM)
    }

    @Test(expected = RuntimeException::class)
    fun validateTritsWithInvalidMetadata() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        TransactionValidator.validate(trits, MAINNET_MWM)
    }

    @Test
    @Throws(Exception::class)
    fun validateBytesWithNewCurl() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        Converter.copyTrits(0, trits, 0, trits.size)
        val bytes = Converter.allocateBytesForTrits(trits.size)
        Converter.bytes(trits, 0, bytes, 0, trits.size)
        TransactionValidator.validate(bytes, txValidator!!.minWeightMagnitude, SpongeFactory.create(SpongeFactory.Mode.CURLP81))
    }

    @Test
    @Throws(Exception::class)
    fun verifyTxIsSolid() {
        val tx = txWithBranchAndTrunk
        assertTrue(txValidator!!.checkSolidity(tx.hash, false))
        assertTrue(txValidator!!.checkSolidity(tx.hash, true))
    }

    @Test
    @Throws(Exception::class)
    fun verifyTxIsNotSolid() {
        val tx = txWithoutBranchAndTrunk
        assertFalse(txValidator!!.checkSolidity(tx.hash, false))
        assertFalse(txValidator!!.checkSolidity(tx.hash, true))
    }

    @Test
    fun addSolidTransactionWithoutErrors() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        Converter.copyTrits(0, trits, 0, trits.size)
        txValidator!!.addSolidTransaction(Hash.calculate(SpongeFactory.Mode.CURLP81, trits))
    }

    companion object {

        private val MAINNET_MWM = 14
        private val dbFolder = TemporaryFolder()
        private val logFolder = TemporaryFolder()
        private var tangle: Tangle? = null
        private var txValidator: TransactionValidator? = null

        @BeforeClass
        @Throws(Exception::class)
        fun setUp() {
            dbFolder.create()
            logFolder.create()
            tangle = Tangle()
            tangle!!.addPersistenceProvider(
                    RocksDBPersistenceProvider(
                            dbFolder.root.absolutePath, logFolder.root.absolutePath, 1000))
            tangle!!.init()
            val tipsViewModel = TipsViewModel()
            val messageQ = MessageQ(0, "", 0, false)
            val txRequester = TransactionRequester(tangle, messageQ)
            txValidator = TransactionValidator(tangle!!, tipsViewModel, txRequester, messageQ,
                    java.lang.Long.parseLong(Configuration.GLOBAL_SNAPSHOT_TIME))
            txValidator!!.init(false, MAINNET_MWM)
        }

        @AfterClass
        @Throws(Exception::class)
        fun tearDown() {
            txValidator!!.shutdown()
            tangle!!.shutdown()
            dbFolder.delete()
            logFolder.delete()
        }
    }
}
