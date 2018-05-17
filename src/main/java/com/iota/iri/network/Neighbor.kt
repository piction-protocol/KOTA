package com.iota.iri.network

import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicInteger

abstract class Neighbor(val address: InetSocketAddress, isConfigured: Boolean) {

    var numberOfAllTransactions: Long = 0
        private set
    var numberOfNewTransactions: Long = 0
        private set
    var numberOfInvalidTransactions: Long = 0
        private set
    var numberOfRandomTransactionRequests: Long = 0
        private set
    var numberOfSentTransactions: Long = 0
        private set

    var isFlagged = false

    val hostAddress: String
    abstract val port: Int


    init {
        this.hostAddress = address.address.hostAddress
        this.isFlagged = isConfigured
    }

    abstract fun send(packet: DatagramPacket)
    abstract fun connectionType(): String
    abstract fun matches(address: SocketAddress): Boolean

    override fun equals(obj: Any?): Boolean {
        return this === obj || !(obj == null || obj.javaClass != this.javaClass) && address == (obj as Neighbor).address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }

    fun incAllTransactions() {
        numberOfAllTransactions++
    }

    fun incNewTransactions() {
        numberOfNewTransactions++
    }

    fun incRandomTransactionRequests() {
        numberOfRandomTransactionRequests++
    }

    fun incInvalidTransactions() {
        numberOfInvalidTransactions++
    }

    fun incSentTransactions() {
        numberOfSentTransactions++
    }

    companion object {

        private val numPeers = AtomicInteger(0)
        fun getNumPeers(): Int {
            return numPeers.get()
        }

        fun incNumPeers() {
            numPeers.incrementAndGet()
        }

        fun decNumPeers() {
            val v = numPeers.decrementAndGet()
            if (v < 0) {
                numPeers.set(0)
            }
        }
    }

}
