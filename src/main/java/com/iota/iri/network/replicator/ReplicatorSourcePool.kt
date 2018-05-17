package com.iota.iri.network.replicator

import com.iota.iri.network.Node
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReplicatorSourcePool(private val replicatorSinkPool: ReplicatorSinkPool,
                           private val node: Node,
                           private val maxPeers: Int,
                           private val testnet: Boolean) : Runnable {
    @Volatile private var shutdown = false
    private var pool: ExecutorService? = null
    private var port: Int = 0

    override fun run() {
        val pool: ExecutorService
        var server: ServerSocket? = null
        pool = Executors.newFixedThreadPool(Replicator.NUM_THREADS)
        this.pool = pool
        try {
            server = ServerSocket(port)
            log.info("TCP replicator is accepting connections on tcp port " + server.localPort)
            while (!shutdown) {
                try {
                    val request = server.accept()
                    request.setSoLinger(true, 0)
                    val proc = ReplicatorSourceProcessor(replicatorSinkPool, request, node, maxPeers, testnet)
                    pool.submit(proc)
                } catch (ex: IOException) {
                    log.error("Error accepting connection", ex)
                }

            }
            log.info("ReplicatorSinkPool shutting down")
        } catch (e: IOException) {
            log.error("***** NETWORK ALERT ***** Cannot create server socket on port {}, {}", port, e.message)
        } finally {
            if (server != null) {
                try {
                    server.close()
                } catch (e: Exception) {
                    // don't care.
                }

            }
        }
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        shutdown = true
        //notify();
        pool?.let {
            it.shutdown()
            it.awaitTermination(6, TimeUnit.SECONDS)
        }
    }

    fun init(port: Int): ReplicatorSourcePool {
        this.port = port
        return this
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReplicatorSourcePool::class.java)
    }
}
