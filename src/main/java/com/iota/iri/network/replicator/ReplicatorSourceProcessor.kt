package com.iota.iri.network.replicator

import com.iota.iri.conf.Configuration
import com.iota.iri.network.Neighbor
import com.iota.iri.network.Node
import com.iota.iri.network.TCPNeighbor
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.zip.CRC32

internal class ReplicatorSourceProcessor(private val replicatorSinkPool: ReplicatorSinkPool,
                                         private val connection: Socket,
                                         private val node: Node,
                                         private val maxPeers: Int,
                                         private val testnet: Boolean) : Runnable {

    private val shutdown = false
    private val packetSize: Int

    private var existingNeighbor: Boolean = false

    private var neighbor: TCPNeighbor? = null

    init {
        this.packetSize = if (testnet)
            Integer.parseInt(Configuration.TESTNET_PACKET_SIZE)
        else
            Integer.parseInt(Configuration.PACKET_SIZE)
    }

    override fun run() {
        var count: Int
        val data = ByteArray(2000)
        var offset = 0
        //boolean isNew;
        var finallyClose = true

        try {

            val address = connection.remoteSocketAddress
            val inet_socket_address = address as InetSocketAddress

            existingNeighbor = false
            val neighbors = node.neighbors
            neighbors.stream().filter { n -> n is TCPNeighbor }
                    .map { n -> n as TCPNeighbor }
                    .forEach { n ->
                        val hisAddress = inet_socket_address.address.hostAddress
                        if (n.hostAddress == hisAddress) {
                            existingNeighbor = true
                            neighbor = n
                        }
                    }

            if (!existingNeighbor) {
                val maxPeersAllowed = maxPeers
                if (!testnet || Neighbor.getNumPeers() >= maxPeersAllowed) {
                    val hostAndPort = inet_socket_address.hostName + ":" + inet_socket_address.port.toString()
                    if (Node.rejectedAddresses.add(inet_socket_address.hostName)) {
                        var sb = ("***** NETWORK ALERT ***** Got connected from unknown neighbor tcp://"
                                + hostAndPort
                                + " (" + inet_socket_address.address.hostAddress + ") - closing connection")
                        if (testnet && Neighbor.getNumPeers() >= maxPeersAllowed) {
                            sb = sb + (" (max-peers allowed is " + maxPeersAllowed.toString() + ")")
                        }
                        log.info(sb)
                    }
                    connection.getInputStream().close()
                    connection.shutdownInput()
                    connection.shutdownOutput()
                    connection.close()
                    return
                } else {
                    val fresh_neighbor = TCPNeighbor(inet_socket_address, false)
                    node.neighbors.add(fresh_neighbor)
                    neighbor = fresh_neighbor
                    Neighbor.incNumPeers()
                }
            }

            if (neighbor!!.source != null) {
                log.info("Source {} already connected", inet_socket_address.address.hostAddress)
                finallyClose = false
                return
            }
            neighbor!!.source = connection

            // Read neighbors tcp listener port number.
            val stream = connection.getInputStream()
            offset = 0
            count = stream.read(data, offset, ReplicatorSinkPool.PORT_BYTES - offset)
            while (count != -1 && offset < ReplicatorSinkPool.PORT_BYTES) {
                offset += count
            }

            if (count == -1 || connection.isClosed) {
                log.error("Did not receive neighbors listener port")
                return
            }

            val pbytes = ByteArray(10)
            System.arraycopy(data, 0, pbytes, 0, ReplicatorSinkPool.PORT_BYTES)
            neighbor!!.setTcpPort(java.lang.Long.parseLong(String(pbytes)).toInt())

            if (neighbor!!.sink == null) {
                log.info("Creating sink for {}", neighbor!!.hostAddress)
                replicatorSinkPool.createSink(neighbor!!)
            }

            if (connection.isConnected) {
                log.info("----- NETWORK INFO ----- Source {} is connected", inet_socket_address.address.hostAddress)
            }

            connection.soTimeout = 0  // infinite timeout - blocking read

            offset = 0
            while (!shutdown && !neighbor!!.isStopped) {
                count = stream.read(data, offset, packetSize - offset + ReplicatorSinkProcessor.CRC32_BYTES)
                while (count != -1 && offset < packetSize + ReplicatorSinkProcessor.CRC32_BYTES) {
                    offset += count
                }

                if (count == -1 || connection.isClosed) {
                    break
                }

                offset = 0

                try {
                    val crc32 = CRC32()
                    for (i in 0 until packetSize) {
                        crc32.update(data[i].toInt())
                    }
                    var crc32_string = java.lang.Long.toHexString(crc32.value)
                    while (crc32_string.length < ReplicatorSinkProcessor.CRC32_BYTES) {
                        crc32_string = "0" + crc32_string
                    }
                    val crc32_bytes = crc32_string.toByteArray()

                    var crcError = false
                    for (i in 0 until ReplicatorSinkProcessor.CRC32_BYTES) {
                        if (crc32_bytes[i] != data[packetSize + i]) {
                            crcError = true
                            break
                        }
                    }
                    if (!crcError) {
                        node.preProcessReceivedData(data, address, "tcp")
                    }
                } catch (e: IllegalStateException) {
                    log.error("Queue is full for neighbor IP {}", inet_socket_address.address.hostAddress)
                } catch (e: RuntimeException) {
                    log.error("Transaction processing runtime exception ", e)
                    neighbor!!.incInvalidTransactions()
                } catch (e: Exception) {
                    log.info("Transaction processing exception " + e.message)
                    log.error("Transaction processing exception ", e)
                }

            }
        } catch (e: IOException) {
            log.error("***** NETWORK ALERT ***** TCP connection reset by neighbor {}, source closed, {}", neighbor!!.hostAddress, e.message)
            replicatorSinkPool.shutdownSink(neighbor!!)
        } finally {
            if (neighbor != null) {
                if (finallyClose) {
                    replicatorSinkPool.shutdownSink(neighbor!!)
                    neighbor!!.source = null
                    neighbor!!.sink = null
                    //if (!neighbor.isFlagged() ) {
                    //   Node.instance().getNeighbors().remove(neighbor);
                    //}
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ReplicatorSourceProcessor::class.java)
    }
}
