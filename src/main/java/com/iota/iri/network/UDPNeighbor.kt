package com.iota.iri.network

import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketAddress

/**
 * Created by paul on 4/15/17.
 */
class UDPNeighbor internal constructor(address: InetSocketAddress, private val socket: DatagramSocket, isConfigured: Boolean) : Neighbor(address, isConfigured) {

    override val port: Int
        get() = address.port

    /**
     * This is a blocking write and it is not necessary to copy the sent data.
     *
     * @param packet the packet to be sent immediately.
     */
    override fun send(packet: DatagramPacket) {
        try {
            packet.socketAddress = address
            socket.send(packet)
            incSentTransactions()
        } catch (e: Exception) {
            log.error("Error sending UDP packet to [{}]: {}", address, e.toString())
        }

    }

    override fun connectionType(): String {
        return "udp"
    }

    override fun matches(address: SocketAddress): Boolean {
        if (this.address.toString().contains(address.toString())) {
            val port = this.address.port
            if (address.toString().contains(Integer.toString(port))) {
                return true
            }
        }
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(UDPNeighbor::class.java)
    }

}