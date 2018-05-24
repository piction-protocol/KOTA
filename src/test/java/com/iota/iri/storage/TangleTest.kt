package com.iota.iri.storage

import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.controllers.TransactionViewModelTest
import com.iota.iri.hash.Sponge
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.model.Transaction
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import com.iota.iri.utils.Converter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.*

/**
 * Created by paul on 3/3/17 for iri.
 */
class TangleTest {

    private val tangle = Tangle()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val dbFolder = TemporaryFolder()
        val logFolder = TemporaryFolder()
        dbFolder.create()
        logFolder.create()
        val rocksDBPersistenceProvider: RocksDBPersistenceProvider
        rocksDBPersistenceProvider = RocksDBPersistenceProvider(dbFolder.root.absolutePath,
                logFolder.root.absolutePath, 1000)
        tangle.addPersistenceProvider(rocksDBPersistenceProvider)
        tangle.init()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        tangle.shutdown()
    }

    @Test
    @Throws(Exception::class)
    fun save() {
        val transaction = Transaction()
        val r = Random()
        val hash = IntArray(Sponge.HASH_LENGTH)
        val trits = Arrays.stream(IntArray(TransactionViewModel.TRINARY_SIZE))
                .map { i -> r.nextInt(3) - 1 }.toArray()
        val curl = SpongeFactory.create(SpongeFactory.Mode.CURLP81)
        curl!!.absorb(trits, 0, trits.size)
        curl.squeeze(hash, 0, Sponge.HASH_LENGTH)
        transaction.bytes = Converter.allocateBytesForTrits(trits.size)
        Converter.bytes(trits, transaction.bytes)
    }

    @Test
    @Throws(Exception::class)
    fun getKeysStartingWithValue() {
        val trits = TransactionViewModelTest.randomTransactionTrits
        val transactionViewModel = TransactionViewModel(trits, Hash.calculate(SpongeFactory.Mode.CURLP81, trits))
        transactionViewModel.store(tangle)
    }
}