package com.iota.iri.controllers

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ExecutionException

/**
 * Created by paul on 5/2/17.
 */
class TipsViewModelTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {

    }

    @After
    @Throws(Exception::class)
    fun tearDown() {

    }

    @Test
    @Throws(Exception::class)
    fun addTipHash() {

    }

    @Test
    @Throws(Exception::class)
    fun removeTipHash() {

    }

    @Test
    @Throws(Exception::class)
    fun setSolid() {

    }

    @Test
    @Throws(Exception::class)
    fun getTips() {

    }

    @Test
    @Throws(Exception::class)
    fun getRandomSolidTipHash() {

    }

    @Test
    @Throws(Exception::class)
    fun getRandomNonSolidTipHash() {

    }

    @Test
    @Throws(Exception::class)
    fun getRandomTipHash() {

    }

    @Test
    @Throws(Exception::class)
    fun nonSolidSize() {

    }

    @Test
    @Throws(Exception::class)
    fun size() {

    }

    @Test
    @Throws(Exception::class)
    fun loadTipHashes() {

    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun nonsolidCapacityLimited() {
        val tipsVM = TipsViewModel()
        val capacity = TipsViewModel.MAX_TIPS
        //fill tips list
        for (i in 0 until capacity * 2) {
            val hash = TransactionViewModelTest.randomTransactionHash
            tipsVM.addTipHash(hash)
        }
        //check that limit wasn't breached
        assertEquals(capacity.toLong(), tipsVM.nonSolidSize().toLong())
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun solidCapacityLimited() {
        val tipsVM = TipsViewModel()
        val capacity = TipsViewModel.MAX_TIPS
        //fill tips list
        for (i in 0 until capacity * 2) {
            val hash = TransactionViewModelTest.randomTransactionHash
            tipsVM.addTipHash(hash)
            tipsVM.setSolid(hash)
        }
        //check that limit wasn't breached
        assertEquals(capacity.toLong(), tipsVM.size().toLong())
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun totalCapacityLimited() {
        val tipsVM = TipsViewModel()
        val capacity = TipsViewModel.MAX_TIPS
        //fill tips list
        for (i in 0..capacity * 4) {
            val hash = TransactionViewModelTest.randomTransactionHash
            tipsVM.addTipHash(hash)
            if (i % 2 == 1) {
                tipsVM.setSolid(hash)
            }
        }
        //check that limit wasn't breached
        assertEquals((capacity * 2).toLong(), tipsVM.size().toLong())
    }

}