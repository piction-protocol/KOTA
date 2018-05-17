package com.iota.iri.controllers

import com.iota.iri.conf.Configuration
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.model.Transaction
import com.iota.iri.network.TransactionRequester
import com.iota.iri.storage.Tangle
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import com.iota.iri.utils.Converter
import org.junit.*
import org.junit.rules.TemporaryFolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.*

import org.junit.Assert.*

/**
 * Created by paul on 3/5/17 for iri.
 */
class TransactionViewModelTest {
    internal var log = LoggerFactory.getLogger(TransactionViewModelTest::class.java)

    @Test
    @Throws(Exception::class)
    fun getBundleTransactions() {
    }

    @Test
    @Throws(Exception::class)
    fun getBranchTransaction() {
    }

    @Test
    @Throws(Exception::class)
    fun getTrunkTransaction() {
    }

    @Test
    @Throws(Exception::class)
    fun getApprovers() {
        val transactionViewModel: TransactionViewModel
        val otherTxVM: TransactionViewModel
        val trunkTx: TransactionViewModel
        val branchTx: TransactionViewModel


        val trits = randomTransactionTrits
        trunkTx = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))

        branchTx = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))

        var childTx = randomTransactionTrits
        System.arraycopy(trunkTx.hash.trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE)
        System.arraycopy(branchTx.hash.trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE)
        transactionViewModel = TransactionViewModel(childTx, Hash.calculate(SpongeFactory.Mode.CURLP81, childTx))

        childTx = randomTransactionTrits
        System.arraycopy(trunkTx.hash.trits(), 0, childTx, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE)
        System.arraycopy(branchTx.hash.trits(), 0, childTx, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE)
        otherTxVM = TransactionViewModel(childTx, Hash.calculate(SpongeFactory.Mode.CURLP81, childTx))

        otherTxVM.store(tangle)
        transactionViewModel.store(tangle)
        trunkTx.store(tangle)
        branchTx.store(tangle)

        val approvers = trunkTx.getApprovers(tangle).getHashes()
        assertNotEquals(approvers.size.toLong(), 0)
    }

    @Test
    @Throws(Exception::class)
    fun fromHash() {

    }

    @Test
    @Throws(Exception::class)
    fun fromHash1() {

    }

    @Test
    @Throws(Exception::class)
    fun update() {

    }

    @Test
    @Throws(Exception::class)
    fun trits() {
        /*
        int[] blanks = new int[13];
        for(int i=0; i++ < 1000;) {
            int[] trits = getRandomTransactionTrits(seed), searchTrits;
            System.arraycopy(new int[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET-blanks.length, blanks.length);
            System.arraycopy(blanks, 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET + TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE-blanks.length, blanks.length);
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits);
            transactionViewModel.store();
            assertArrayEquals(transactionViewModel.trits(), TransactionViewModel.fromHash(transactionViewModel.getHash()).trits());
        }
        */
    }

    @Test
    @Throws(Exception::class)
    fun getBytes() {
        /*
        for(int i=0; i++ < 1000;) {
            int[] trits = getRandomTransactionTrits(seed);
            System.arraycopy(new int[TransactionViewModel.VALUE_TRINARY_SIZE], 0, trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_TRINARY_SIZE);
            Converter.copyTrits(seed.nextLong(), trits, TransactionViewModel.VALUE_TRINARY_OFFSET, TransactionViewModel.VALUE_USABLE_TRINARY_SIZE);
            TransactionViewModel transactionViewModel = new TransactionViewModel(trits);
            transactionViewModel.store();
            assertArrayEquals(transactionViewModel.getBytes(), TransactionViewModel.fromHash(transactionViewModel.getHash()).getBytes());
        }
        */
    }

    @Test
    @Throws(Exception::class)
    fun getHash() {

    }

    @Test
    @Throws(Exception::class)
    fun getAddress() {

    }

    @Test
    @Throws(Exception::class)
    fun getTag() {

    }

    @Test
    @Throws(Exception::class)
    fun getBundleHash() {

    }

    @Test
    @Throws(Exception::class)
    fun getTrunkTransactionHash() {
    }

    @Test
    @Throws(Exception::class)
    fun getBranchTransactionHash() {

    }

    @Test
    @Throws(Exception::class)
    fun getValue() {

    }

    @Test
    @Throws(Exception::class)
    fun value() {

    }

    @Test
    @Throws(Exception::class)
    fun setValidity() {

    }

    @Test
    @Throws(Exception::class)
    fun getValidity() {

    }

    @Test
    @Throws(Exception::class)
    fun getCurrentIndex() {

    }

    @Test
    @Throws(Exception::class)
    fun getLastIndex() {

    }

    @Test
    @Throws(Exception::class)
    fun mightExist() {

    }

    @Test
    @Throws(Exception::class)
    fun update1() {

    }

    @Test
    @Throws(Exception::class)
    fun setAnalyzed() {

    }


    @Test
    @Throws(Exception::class)
    fun dump() {

    }

    @Test
    @Throws(Exception::class)
    fun store() {

    }

    @Test
    @Throws(Exception::class)
    fun updateTips() {

    }

    @Test
    @Throws(Exception::class)
    fun updateReceivedTransactionCount() {

    }

    @Test
    @Throws(Exception::class)
    fun updateApprovers() {

    }

    @Test
    @Throws(Exception::class)
    fun hashesFromQuery() {

    }

    @Test
    @Throws(Exception::class)
    fun approversFromHash() {

    }

    @Test
    @Throws(Exception::class)
    fun fromTag() {

    }

    @Test
    @Throws(Exception::class)
    fun fromBundle() {

    }

    @Test
    @Throws(Exception::class)
    fun fromAddress() {

    }

    @Test
    @Throws(Exception::class)
    fun getTransactionAnalyzedFlag() {

    }

    @Test
    @Throws(Exception::class)
    fun getType() {

    }

    @Test
    @Throws(Exception::class)
    fun setArrivalTime() {

    }

    @Test
    @Throws(Exception::class)
    fun getArrivalTime() {

    }

    @Test
    @Throws(Exception::class)
    fun updateHeightShouldWork() {
        val count = 4
        val transactionViewModels = arrayOfNulls<TransactionViewModel>(count)
        var hash = randomTransactionHash
        transactionViewModels[0] = TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH,
                Hash.NULL_HASH), hash)
        transactionViewModels[0]?.store(tangle)
        run {
            var i = 0
            while (++i < count) {
                transactionViewModels[i] = TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                        Hash.NULL_HASH), randomTransactionHash)
                transactionViewModels[i]?.store(tangle)
            }
        }

        transactionViewModels[count - 1]?.updateHeights(tangle)

        var i = count
        while (i > 1) {
            assertEquals(i.toLong(), TransactionViewModel.fromHash(tangle, transactionViewModels[--i]?.getHash()).height)
        }
    }

    @Test
    @Throws(Exception::class)
    fun updateHeightPrefilledSlotShouldFail() {
        val count = 4
        val transactionViewModels = arrayOfNulls<TransactionViewModel>(count)
        var hash = randomTransactionHash
        run {
            var i = 0
            while (++i < count) {
                transactionViewModels[i] = TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hash,
                        Hash.NULL_HASH), randomTransactionHash)
                transactionViewModels[i]?.store(tangle)
            }
        }

        transactionViewModels[count - 1]?.updateHeights(tangle)

        var i = count
        while (i > 1) {
            assertEquals(0, TransactionViewModel.fromHash(tangle, transactionViewModels[--i]?.getHash()).height)
        }
    }

    @Test
    @Throws(Exception::class)
    fun findShouldBeSuccessful() {
        val trits = randomTransactionTrits
        val transactionViewModel = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))
        transactionViewModel.store(tangle)
        val hash = transactionViewModel.hash
        Assert.assertArrayEquals(TransactionViewModel.find(tangle, Arrays.copyOf(hash.bytes(), Integer.parseInt(Configuration.REQ_HASH_SIZE))).bytes, transactionViewModel.bytes)
    }

    @Test
    @Throws(Exception::class)
    fun findShouldReturnNull() {
        var trits = randomTransactionTrits
        val transactionViewModel = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))
        trits = randomTransactionTrits
        val transactionViewModelNoSave = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))
        transactionViewModel.store(tangle)
        val hash = transactionViewModelNoSave.hash
        Assert.assertFalse(Arrays.equals(TransactionViewModel.find(tangle, Arrays.copyOf(hash.bytes(), Integer.parseInt(Configuration.REQ_HASH_SIZE))).bytes, transactionViewModel.bytes))
    }

    //@Test
    @Throws(Exception::class)
    fun testManyTXInDB() {
        var i: Int
        var j: Int
        val hashes = LinkedList<Hash>()
        var hash: Hash
        hash = randomTransactionHash
        hashes.add(hash)
        var start: Long
        var diff: Long
        var diffget: Long
        var subSumDiff: Long = 0
        var maxdiff: Long = 0
        var sumdiff: Long = 0
        val max = 990 * 1000
        val interval1 = 50
        val interval = interval1 * 10
        log.info("Starting Test. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle))
        TransactionViewModel(getRandomTransactionWithTrunkAndBranch(Hash.NULL_HASH, Hash.NULL_HASH), hash).store(tangle)
        var transactionViewModel: TransactionViewModel
        val pop = false
        i = 0
        while (i++ < max) {
            hash = randomTransactionHash
            j = hashes.size
            transactionViewModel = TransactionViewModel(getRandomTransactionWithTrunkAndBranch(hashes[seed.nextInt(j)], hashes[seed.nextInt(j)]), hash)
            start = System.nanoTime()
            transactionViewModel.store(tangle)
            diff = System.nanoTime() - start
            subSumDiff += diff
            if (diff > maxdiff) {
                maxdiff = diff
            }
            hash = hashes[seed.nextInt(j)]
            start = System.nanoTime()
            TransactionViewModel.fromHash(tangle, hash)
            diffget = System.nanoTime() - start
            hashes.add(hash)
            if (pop || i > 1000) {
                hashes.removeFirst()
            }

            //log.info("{}", new String(new char[(int) ((diff/ 10000))]).replace('\0', '|'));
            if (i % interval1 == 0) {
                //log.info("{}", new String(new char[(int) (diff / 50000)]).replace('\0', '-'));
                //log.info("{}", new String(new char[(int) ((subSumDiff / interval1 / 100000))]).replace('\0', '|'));
                sumdiff += subSumDiff
                subSumDiff = 0
            }
            if (i % interval == 0) {
                log.info("Save time for {}: {} us.\tGet Time: {} us.\tMax time: {} us. Average: {}", i,
                        diff / 1000, diffget / 1000, maxdiff / 1000, sumdiff / interval.toLong() / 1000)
                sumdiff = 0
                maxdiff = 0
            }
        }
        log.info("Done. #TX: {}", TransactionViewModel.getNumberOfStoredTransactions(tangle))
    }

    private fun getRandomTransaction(seed: Random): Transaction {
        val transaction = Transaction()

        val trits = Arrays.stream(IntArray(TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE)).map { i -> seed.nextInt(3) - 1 }.toArray()
        transaction.bytes = Converter.allocateBytesForTrits(trits.size)
        Converter.bytes(trits, 0, transaction.bytes, 0, trits.size)
        return transaction
    }

    companion object {

        private val dbFolder = TemporaryFolder()
        private val logFolder = TemporaryFolder()
        private val tangle = Tangle()

        private val seed = Random()

        @BeforeClass
        @Throws(Exception::class)
        fun setUp() {
            dbFolder.create()
            logFolder.create()
            val rocksDBPersistenceProvider: RocksDBPersistenceProvider
            rocksDBPersistenceProvider = RocksDBPersistenceProvider(dbFolder.root.absolutePath,
                    logFolder.root.absolutePath, 1000)
            tangle.addPersistenceProvider(rocksDBPersistenceProvider)
            tangle.init()
        }

        @AfterClass
        @Throws(Exception::class)
        fun tearDown() {
            tangle.shutdown()
            dbFolder.delete()
            logFolder.delete()
        }

        fun getRandomTransactionWithTrunkAndBranch(trunk: Hash, branch: Hash): IntArray {
            val trits = randomTransactionTrits
            System.arraycopy(trunk.trits(), 0, trits, TransactionViewModel.TRUNK_TRANSACTION_TRINARY_OFFSET,
                    TransactionViewModel.TRUNK_TRANSACTION_TRINARY_SIZE)
            System.arraycopy(branch.trits(), 0, trits, TransactionViewModel.BRANCH_TRANSACTION_TRINARY_OFFSET,
                    TransactionViewModel.BRANCH_TRANSACTION_TRINARY_SIZE)
            return trits
        }

        val randomTransactionTrits: IntArray
            get() = Arrays.stream(IntArray(TransactionViewModel.TRINARY_SIZE)).map { i -> seed.nextInt(3) - 1 }.toArray()
        val randomTransactionHash: Hash
            get() = Hash(Arrays.stream(IntArray(Hash.SIZE_IN_TRITS)).map { i -> seed.nextInt(3) - 1 }.toArray())
    }
}