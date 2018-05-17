package com.iota.iri.controllers

import com.iota.iri.network.TransactionRequester
import com.iota.iri.storage.Tangle
import com.iota.iri.zmq.MessageQ
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Created by paul on 5/2/17.
 */
class TransactionRequesterTest {
    private val mq: MessageQ? = null

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
    fun init() {

    }

    @Test
    @Throws(Exception::class)
    fun rescanTransactionsToRequest() {

    }

    @Test
    @Throws(Exception::class)
    fun getRequestedTransactions() {

    }

    @Test
    @Throws(Exception::class)
    fun numberOfTransactionsToRequest() {

    }

    @Test
    @Throws(Exception::class)
    fun clearTransactionRequest() {

    }

    @Test
    @Throws(Exception::class)
    fun requestTransaction() {

    }

    @Test
    @Throws(Exception::class)
    fun transactionToRequest() {

    }

    @Test
    @Throws(Exception::class)
    fun checkSolidity() {

    }

    @Test
    @Throws(Exception::class)
    fun instance() {

    }

    @Test
    @Throws(Exception::class)
    fun nonMilestoneCapacityLimited() {
        val txReq = TransactionRequester(tangle, mq)
        val capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE
        //fill tips list
        for (i in 0 until capacity * 2) {
            val hash = TransactionViewModelTest.randomTransactionHash
            txReq.requestTransaction(hash, false)
        }
        //check that limit wasn't breached
        assertEquals(capacity.toLong(), txReq.numberOfTransactionsToRequest().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun milestoneCapacityNotLimited() {
        val txReq = TransactionRequester(tangle, mq)
        val capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE
        //fill tips list
        for (i in 0 until capacity * 2) {
            val hash = TransactionViewModelTest.randomTransactionHash
            txReq.requestTransaction(hash, true)
        }
        //check that limit was surpassed
        assertEquals((capacity * 2).toLong(), txReq.numberOfTransactionsToRequest().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun mixedCapacityLimited() {
        val txReq = TransactionRequester(tangle, mq)
        val capacity = TransactionRequester.MAX_TX_REQ_QUEUE_SIZE
        //fill tips list
        for (i in 0 until capacity * 4) {
            val hash = TransactionViewModelTest.randomTransactionHash
            txReq.requestTransaction(hash, i % 2 == 1)

        }
        //check that limit wasn't breached
        assertEquals((capacity + capacity * 2).toLong(), txReq.numberOfTransactionsToRequest().toLong())
    }

    companion object {
        private val tangle = Tangle()
    }

}