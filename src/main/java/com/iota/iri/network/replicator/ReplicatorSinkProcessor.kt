package com.iota.iri.network.replicator

import com.iota.iri.network.TCPNeighbor
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.CRC32

internal class ReplicatorSinkProcessor(private val neighbor: TCPNeighbor,
                                       private val replicatorSinkPool: ReplicatorSinkPool,
                                       private val port: Int, private val transactionPacketSize: Int) : Runnable {

    override fun run() {
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            log.info("Interrupted")
        }

        val remoteAddress = neighbor.hostAddress

        try {
            var socket: Socket
            synchronized(neighbor) {
                val sink = neighbor.sink
                if (sink == null) {
                    log.info("Opening sink {}", remoteAddress)
                    socket = Socket()
                    socket.setSoLinger(true, 0)
                    socket.soTimeout = 30000
                    neighbor.sink = socket
                    connectToSocket(socket, remoteAddress)
                } else {
                    log.info("Sink {} already created", remoteAddress)
                    return
                }
            }
        } catch (e: Exception) {
            var reason: String? = e.message
            if (reason == null || reason == "null") {
                reason = "closed"
            }
            log.error("***** NETWORK ALERT ***** No sink to host {}:{}, reason: {}", remoteAddress, neighbor.port,
                    reason)
            synchronized(neighbor) {
                val sourceSocket = neighbor.source
                if (sourceSocket != null && (sourceSocket.isClosed || !sourceSocket.isConnected)) {
                    neighbor.source = null
                }
                neighbor.sink = null
            }
        }
    }

    private fun connectToSocket(socket: Socket, remoteAddress: String) {
        log.info("Connecting sink {}", remoteAddress)
        socket.connect(InetSocketAddress(remoteAddress, neighbor.port), 30000)
        if (!socket.isClosed && socket.isConnected) {
            var out = socket.getOutputStream()
            log.info("----- NETWORK INFO ----- Sink {} is connected", remoteAddress)

            // Let neighbor know our tcp listener port
            val fmt = "%0" + ReplicatorSinkPool.PORT_BYTES.toString() + "d"
            val portAsByteArray = ByteArray(10)
            System.arraycopy(String.format(fmt, port).toByteArray(), 0,
                    portAsByteArray, 0, ReplicatorSinkPool.PORT_BYTES)
            out.write(portAsByteArray)

            while (!replicatorSinkPool.shutdown && !neighbor.isStopped) {
                try {
                    val message = neighbor.nextMessage
                    if (neighbor.sink != null) {
                        if (neighbor.sink!!.isClosed || !neighbor.sink!!.isConnected) {
                            log.info("----- NETWORK INFO ----- Sink {} got disconnected", remoteAddress)
                            return
                        } else {
                            if (neighbor.sink != null && neighbor.sink!!.isConnected
                                    && neighbor.source != null && neighbor.source!!.isConnected) {

                                val bytes = message.array()

                                if (bytes.size == transactionPacketSize) {
                                    try {
                                        val crc32 = CRC32()
                                        crc32.update(message.array())
                                        var crc32_string = java.lang.Long.toHexString(crc32.value)
                                        while (crc32_string.length < CRC32_BYTES) {
                                            crc32_string = "0" + crc32_string
                                        }
                                        out.write(message.array())
                                        out.write(crc32_string.toByteArray())
                                        out.flush()
                                        neighbor.incSentTransactions()
                                    } catch (e2: IOException) {
                                        if (!neighbor.sink!!.isClosed && neighbor.sink!!.isConnected) {
                                            out.close()
                                            out = neighbor.sink!!.getOutputStream()
                                        } else {
                                            log.info("----- NETWORK INFO ----- Sink {} thread terminating",
                                                    remoteAddress)
                                            return
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    log.error("Interrupted while waiting for send buffer")
                }
            }
        }
    }

    companion object {

        private val log = LoggerFactory.getLogger(ReplicatorSinkProcessor::class.java)

        val CRC32_BYTES = 16
    }
}
