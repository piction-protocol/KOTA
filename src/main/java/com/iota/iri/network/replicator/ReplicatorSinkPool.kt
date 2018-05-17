package com.iota.iri.network.replicator

import com.iota.iri.network.Node
import com.iota.iri.network.TCPNeighbor
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReplicatorSinkPool(private val node: Node, private val port: Int, private val transactionPacketSize: Int) : Runnable {

    private var sinkPool: ExecutorService? = null

    var shutdown = false

    override fun run() {

        sinkPool = Executors.newFixedThreadPool(Replicator.NUM_THREADS)
        run {
            val neighbors = node.neighbors
            // wait until list is populated
            var loopcnt = 10
            while (loopcnt-- > 0 && neighbors.size == 0) {
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    log.error("Interrupted")
                }

            }
            neighbors.stream().filter { n -> n is TCPNeighbor && n.isFlagged }
                    .map { n -> n as TCPNeighbor }
                    .forEach({ this.createSink(it) })
        }

        while (!Thread.interrupted()) {
            // Restart attempt for neighbors that are in the configuration.
            try {
                Thread.sleep(30000)
            } catch (e: InterruptedException) {
                log.debug("Interrupted: ", e)
            }

            val neighbors = node.neighbors
            neighbors.stream()
                    .filter { n -> n is TCPNeighbor && n.isFlagged }
                    .map { n -> n as TCPNeighbor }
                    .filter { n -> n.sink == null }
                    .forEach({ this.createSink(it) })
        }
    }

    fun createSink(neighbor: TCPNeighbor) {
        val proc = ReplicatorSinkProcessor(neighbor, this, port, transactionPacketSize)
        sinkPool!!.submit(proc)
    }

    fun shutdownSink(neighbor: TCPNeighbor) {
        val socket = neighbor.sink
        socket?.let {
            if (!socket.isClosed) {
                try {
                    socket.close()
                    log.info("Sink {} closed", neighbor.hostAddress)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        neighbor.sink = null
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        shutdown = true
        sinkPool?.let {
            it.shutdown()
            it.awaitTermination(6, TimeUnit.SECONDS)
        }
    }

    companion object {

        private val log = LoggerFactory.getLogger(ReplicatorSinkPool::class.java)

        val PORT_BYTES = 10
    }
}
