package com.iota.iri.controllers

import com.iota.iri.storage.Tangle
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Created by paul on 5/2/17.
 */
class BundleViewModelTest {

    @Before
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

    @After
    @Throws(Exception::class)
    fun tearDown() {
        tangle.shutdown()
        dbFolder.delete()
        logFolder.delete()
    }

    @Test
    @Throws(Exception::class)
    fun quietFromHash() {

    }

    @Test
    @Throws(Exception::class)
    fun fromHash() {

    }

    @Test
    @Throws(Exception::class)
    fun getTransactionViewModels() {

    }

    @Test
    @Throws(Exception::class)
    fun quietGetTail() {

    }

    @Test
    @Throws(Exception::class)
    fun getTail() {

    }

    companion object {
        private val dbFolder = TemporaryFolder()
        private val logFolder = TemporaryFolder()
        private val tangle = Tangle()
    }

}