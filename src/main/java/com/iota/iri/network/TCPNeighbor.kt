package com.iota.iri.network

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Created by paul on 4/15/17.
 */
class TCPNeighbor(address: InetSocketAddress, isConfigured: Boolean) : Neighbor(address, isConfigured) {
    override var port: Int = 0
        private set

    private val sendQueue = ArrayBlockingQueue<ByteBuffer>(10)
    var isStopped = false
        private set

    var source: Socket? = null
        set(source) {
            if (source == null) {
                if (this.source != null && !this.source!!.isClosed) {
                    try {
                        this.source!!.close()
                        log.info("Source {} closed", this.hostAddress)
                    } catch (e: IOException) {
                        log.info("Source {} close failure {}", this.hostAddress)
                    }

                }
            }
            field = source
        }

    var sink: Socket? = null
        set(sink) {
            if (sink == null) {
                if (this.sink != null && !this.sink!!.isClosed) {
                    try {
                        this.sink!!.close()
                        log.info("Sink {} closed", this.hostAddress)
                    } catch (e: IOException) {
                        log.info("Source {} close failure {}", this.hostAddress)
                    }

                }
            }
            field = sink
        }

    val nextMessage: ByteBuffer
        @Throws(InterruptedException::class)
        get() = this.sendQueue.poll(10000, TimeUnit.MILLISECONDS)

    init {
        this.port = address.port
    }

    fun clear() {
        source = null
        sink = null
        this.isStopped = true
    }

    /**
     * This is a non-blocking write and that makes it necessary to make a defensive copy of the sent data.
     *
     * @param packet the data to be queued for sending.
     */
    override fun send(packet: DatagramPacket) {
        if (sendQueue.remainingCapacity() == 0) {
            sendQueue.poll()
        }
        val bytes = packet.data.clone()
        sendQueue.add(ByteBuffer.wrap(bytes))
    }

    override fun connectionType(): String {
        return "tcp"
    }

    fun setTcpPort(tcpPort: Int) {
        this.port = tcpPort
    }

    override fun matches(address: SocketAddress): Boolean {
        if (address.toString().contains(this.hostAddress)) {
            val port = this.source!!.port
            if (address.toString().contains(Integer.toString(port))) {
                return true
            }
        }
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(Neighbor::class.java)
    }
}