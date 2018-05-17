package com.iota.iri.network

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by paul on 4/16/17.
 */
class UDPReceiver(private val port: Int, private val node: Node, private val packetSize: Int) {

    private val receivingPacket: DatagramPacket

    private val shuttingDown = AtomicBoolean(false)

    private var socket: DatagramSocket? = null

    private val PROCESSOR_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() * 4)

    private val processor = ThreadPoolExecutor(PROCESSOR_THREADS, PROCESSOR_THREADS, 5000L,
            TimeUnit.MILLISECONDS, ArrayBlockingQueue(PROCESSOR_THREADS, true),
            ThreadPoolExecutor.AbortPolicy())

    private var receivingThread: Thread? = null

    init {
        this.receivingPacket = DatagramPacket(ByteArray(packetSize), packetSize)
    }

    @Throws(Exception::class)
    fun init() {
        socket = DatagramSocket(port)
        node.udpSocket = socket
        log.info("UDP replicator is accepting connections on udp port " + port)

        receivingThread = Thread(spawnReceiverThread(), "UDP receiving thread")
        receivingThread?.start()
    }

    private fun spawnReceiverThread(): Runnable {
        return Runnable {
            log.info("Spawning Receiver Thread")

            var processed = 0
            var dropped = 0

            while (!shuttingDown.get()) {

                if ((processed + dropped) % 50000 == 0) {
                    log.info("Receiver thread processed/dropped ratio: $processed/$dropped")
                    processed = 0
                    dropped = 0
                }

                try {
                    socket?.receive(receivingPacket)

                    if (receivingPacket.length == packetSize) {

                        val bytes = Arrays.copyOf(receivingPacket.data, receivingPacket.length)
                        val address = receivingPacket.socketAddress

                        processor.submit { node.preProcessReceivedData(bytes, address, "udp") }
                        processed++

                        Thread.yield()

                    } else {
                        receivingPacket.length = packetSize
                    }
                } catch (e: RejectedExecutionException) {
                    //no free thread, packet dropped
                    dropped++

                } catch (e: Exception) {
                    log.error("Receiver Thread Exception:", e)
                }

            }
            log.info("Shutting down spawning Receiver Thread")
        }
    }

    fun send(packet: DatagramPacket) {
        try {
            socket?.let { it.send(packet) }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        shuttingDown.set(true)
        processor.shutdown()
        processor.awaitTermination(6, TimeUnit.SECONDS)
        try {
            receivingThread?.join(6000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(UDPReceiver::class.java)
    }
}
