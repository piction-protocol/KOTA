package com.iota.iri.network.replicator

import com.iota.iri.network.Node
import org.slf4j.LoggerFactory

class Replicator(node: Node, private val port: Int, maxPeers: Int, testnet: Boolean, transactionPacketSize: Int) {
    private val replicatorSinkPool: ReplicatorSinkPool
    private val replicatorSourcePool: ReplicatorSourcePool

    init {
        replicatorSinkPool = ReplicatorSinkPool(node, port, transactionPacketSize)
        replicatorSourcePool = ReplicatorSourcePool(replicatorSinkPool, node, maxPeers, testnet)
    }

    fun init() {
        Thread(replicatorSinkPool).start()
        Thread(replicatorSourcePool.init(port)).start()
        log.info("Started ReplicatorSourcePool")
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        // TODO
        replicatorSourcePool.shutdown()
        replicatorSinkPool.shutdown()
    }

    companion object {

        val NUM_THREADS = 32

        private val log = LoggerFactory.getLogger(Replicator::class.java)
    }

}
