package com.iota.iri.network

import com.iota.iri.Milestone
import com.iota.iri.TransactionValidator
import com.iota.iri.compose.OURIFunction
import com.iota.iri.compose.URIFunction
import com.iota.iri.conf.Configuration
import com.iota.iri.controllers.TipsViewModel
import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.hash.SpongeFactory
import com.iota.iri.model.Hash
import com.iota.iri.storage.Tangle
import com.iota.iri.zmq.MessageQ
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.net.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The class node is responsible for managing Thread's connection.
 */
class Node(private val configuration: Configuration,
           private val tangle: Tangle,
           private val transactionValidator: TransactionValidator,
           private val transactionRequester: TransactionRequester,
           private val tipsViewModel: TipsViewModel,
           private val milestone: Milestone,
           private val messageQ: MessageQ
) {
    private val reqHashSize: Int


    private var BROADCAST_QUEUE_SIZE: Int = 0
    private var RECV_QUEUE_SIZE: Int = 0
    private var REPLY_QUEUE_SIZE: Int = 0

    private val shuttingDown = AtomicBoolean(false)

    val neighbors: MutableList<Neighbor> = CopyOnWriteArrayList()
    private val broadcastQueue = weightQueue()
    private val receiveQueue = weightQueueTxPair()
    private val replyQueue = weightQueueHashPair()


    private val sendingPacket: DatagramPacket
    private val tipRequestingPacket: DatagramPacket

    private val executor = Executors.newFixedThreadPool(5)

    private var P_DROP_TRANSACTION: Double = 0.toDouble()
    private var P_SEND_MILESTONE: Double = 0.toDouble()
    private var P_REPLY_RANDOM_TIP: Double = 0.toDouble()
    private var P_PROPAGATE_REQUEST: Double = 0.toDouble()


    private var recentSeenBytes: FIFOCache<ByteBuffer, Hash>? = null

    private var debug: Boolean = false
    var udpSocket: DatagramSocket? = null

    private val neighborIpCache = HashMap<String, String>()

    private val randomTipPointer: Hash
        @Throws(Exception::class)
        get() {
            val tip = if (rnd.nextDouble() < P_SEND_MILESTONE) milestone.latestMilestone else tipsViewModel.randomSolidTipHash
            return tip ?: Hash.NULL_HASH
        }

    val broadcastQueueSize: Int
        get() = broadcastQueue.size

    val receiveQueueSize: Int
        get() = receiveQueue.size

    val replyQueueSize: Int
        get() = replyQueue.size

    init {
        this.reqHashSize = configuration.integer(Configuration.DefaultConfSettings.REQUEST_HASH_SIZE)
        val packetSize = configuration.integer(Configuration.DefaultConfSettings.TRANSACTION_PACKET_SIZE)
        this.sendingPacket = DatagramPacket(ByteArray(packetSize), packetSize)
        this.tipRequestingPacket = DatagramPacket(ByteArray(packetSize), packetSize)

    }

    @Throws(Exception::class)
    fun init() {

        P_DROP_TRANSACTION = configuration.doubling(Configuration.DefaultConfSettings.P_DROP_TRANSACTION.name)
        P_SELECT_MILESTONE = configuration.doubling(Configuration.DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name)
        P_SEND_MILESTONE = configuration.doubling(Configuration.DefaultConfSettings.P_SEND_MILESTONE.name)
        P_REPLY_RANDOM_TIP = configuration.doubling(Configuration.DefaultConfSettings.P_REPLY_RANDOM_TIP.name)
        P_PROPAGATE_REQUEST = configuration.doubling(Configuration.DefaultConfSettings.P_PROPAGATE_REQUEST.name)
        sendLimit = (configuration.doubling(Configuration.DefaultConfSettings.SEND_LIMIT.name) * 1000000 / (configuration.integer(Configuration.DefaultConfSettings.TRANSACTION_PACKET_SIZE) * 8)).toLong()
        debug = configuration.booling(Configuration.DefaultConfSettings.DEBUG)

        REPLY_QUEUE_SIZE = configuration.integer(Configuration.DefaultConfSettings.Q_SIZE_NODE)
        RECV_QUEUE_SIZE = REPLY_QUEUE_SIZE
        BROADCAST_QUEUE_SIZE = RECV_QUEUE_SIZE
        val pDropCacheEntry = configuration.doubling(Configuration.DefaultConfSettings.P_DROP_CACHE_ENTRY.name)
        recentSeenBytes = FIFOCache(configuration.integer(Configuration.DefaultConfSettings.CACHE_SIZE_BYTES), pDropCacheEntry)

        parseNeighborsConfig()

        executor.submit(spawnBroadcasterThread())
        executor.submit(spawnTipRequesterThread())
        executor.submit(spawnNeighborDNSRefresherThread())
        executor.submit(spawnProcessReceivedThread())
        executor.submit(spawnReplyToRequestThread())

        executor.shutdown()
    }

    private fun spawnNeighborDNSRefresherThread(): Runnable {
        return Runnable {
            if (configuration.booling(Configuration.DefaultConfSettings.DNS_RESOLUTION_ENABLED)) {
                log.info("Spawning Neighbor DNS Refresher Thread")

                while (!shuttingDown.get()) {
                    var dnsCounter = 0
                    log.info("Checking Neighbors' Ip...")

                    try {
                        neighbors.forEach { n ->
                            val hostname = n.address.hostName
                            checkIp(hostname).ifPresent { ip ->
                                log.info("DNS Checker: Validating DNS Address '{}' with '{}'", hostname, ip)
                                messageQ.publish("dnscv %s %s", hostname, ip)
                                val neighborAddress = neighborIpCache[hostname]

                                if (neighborAddress == null) {
                                    neighborIpCache.put(hostname, ip)
                                } else {
                                    if (neighborAddress == ip) {
                                        log.info("{} seems fine.", hostname)
                                        messageQ.publish("dnscc %s", hostname)
                                    } else {
                                        if (configuration.booling(Configuration.DefaultConfSettings.DNS_REFRESHER_ENABLED)) {
                                            log.info("IP CHANGED for {}! Updating...", hostname)
                                            messageQ.publish("dnscu %s", hostname)
                                            val protocol = if (n is TCPNeighbor) "tcp://" else "udp://"
                                            val port = ":" + n.address.port

                                            uri(protocol + hostname + port).ifPresent { uri ->
                                                removeNeighbor(uri, n.isFlagged)

                                                uri(protocol + ip + port).ifPresent { nuri ->
                                                    val neighbor = newNeighbor(nuri, n.isFlagged)
                                                    addNeighbor(neighbor)
                                                    neighborIpCache.put(hostname, ip)
                                                }
                                            }
                                        } else {
                                            log.info("IP CHANGED for {}! Skipping... DNS_REFRESHER_ENABLED is false.", hostname)
                                        }
                                    }
                                }
                            }
                        }

                        while (dnsCounter++ < 60 * 30 && !shuttingDown.get()) {
                            Thread.sleep(1000)
                        }
                    } catch (e: Exception) {
                        log.error("Neighbor DNS Refresher Thread Exception:", e)
                    }

                }
                log.info("Shutting down Neighbor DNS Refresher Thread")
            } else {
                log.info("Ignoring DNS Refresher Thread... DNS_RESOLUTION_ENABLED is false")
            }
        }
    }

    private fun checkIp(dnsName: String): Optional<String> {

        if (StringUtils.isEmpty(dnsName)) {
            return Optional.empty()
        }

        val inetAddress: InetAddress
        try {
            inetAddress = java.net.InetAddress.getByName(dnsName)
        } catch (e: UnknownHostException) {
            return Optional.empty()
        }

        val hostAddress = inetAddress.hostAddress

        return if (StringUtils.equals(dnsName, hostAddress)) { // not a DNS...
            Optional.empty()
        } else Optional.of(hostAddress)

    }

    fun preProcessReceivedData(receivedData: ByteArray, senderAddress: SocketAddress, uriScheme: String) {
        var receivedTransactionViewModel: TransactionViewModel? = null
        var receivedTransactionHash: Hash? = null

        var addressMatch = false
        var cached = false

        for (neighbor in neighbors) {
            addressMatch = neighbor.matches(senderAddress)
            if (addressMatch) {
                //Validate transaction
                neighbor.incAllTransactions()
                if (rnd.nextDouble() < P_DROP_TRANSACTION) {
                    //log.info("Randomly dropping transaction. Stand by... ");
                    break
                }
                try {

                    //Transaction bytes

                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.update(receivedData, 0, TransactionViewModel.SIZE)
                    val byteHash = ByteBuffer.wrap(digest.digest())

                    //check if cached
                    synchronized(recentSeenBytes!!) {
                        receivedTransactionHash = recentSeenBytes!![byteHash]
                        cached = receivedTransactionHash != null
                    }

                    if (!cached) {
                        //if not, then validate
                        receivedTransactionViewModel = TransactionViewModel(receivedData, Hash.calculate(receivedData, TransactionViewModel.TRINARY_SIZE, SpongeFactory.create(SpongeFactory.Mode.CURLP81)))
                        receivedTransactionHash = receivedTransactionViewModel.hash
                        TransactionValidator.runValidation(receivedTransactionViewModel, transactionValidator.minWeightMagnitude)

                        synchronized(recentSeenBytes!!) {
                            recentSeenBytes!!.put(byteHash, receivedTransactionHash)
                        }

                        //if valid - add to receive queue (receivedTransactionViewModel, neighbor)
                        addReceivedDataToReceiveQueue(receivedTransactionViewModel, neighbor)

                    }

                } catch (e: NoSuchAlgorithmException) {
                    log.error("MessageDigest: " + e)
                } catch (e: TransactionValidator.StaleTimestampException) {
                    log.debug(e.message)
                    try {
                        transactionRequester.clearTransactionRequest(receivedTransactionHash)
                    } catch (e1: Exception) {
                        log.error(e1.message)
                    }

                    neighbor.incInvalidTransactions()
                } catch (e: RuntimeException) {
                    log.error(e.message)
                    log.error("Received an Invalid TransactionViewModel. Dropping it...")
                    neighbor.incInvalidTransactions()
                    break
                }

                //Request bytes

                //add request to reply queue (requestedHash, neighbor)
                var requestedHash = Hash(receivedData, TransactionViewModel.SIZE, reqHashSize)
                if (requestedHash == receivedTransactionHash) {
                    //requesting a random tip
                    requestedHash = Hash.NULL_HASH
                }

                addReceivedDataToReplyQueue(requestedHash, neighbor)

                //recentSeenBytes statistics

                if (debug) {
                    val hitCount: Long
                    val missCount: Long
                    if (cached) {
                        hitCount = recentSeenBytesHitCount.incrementAndGet()
                        missCount = recentSeenBytesMissCount.get()
                    } else {
                        hitCount = recentSeenBytesHitCount.get()
                        missCount = recentSeenBytesMissCount.incrementAndGet()
                    }
                    if ((hitCount + missCount) % 50000L == 0L) {
                        log.info("RecentSeenBytes cache hit/miss ratio: $hitCount/$missCount")
                        messageQ.publish("hmr %d/%d", hitCount, missCount)
                        recentSeenBytesMissCount.set(0L)
                        recentSeenBytesHitCount.set(0L)
                    }
                }

                break
            }
        }

        if (!addressMatch && configuration.booling(Configuration.DefaultConfSettings.TESTNET)) {
            val maxPeersAllowed = configuration.integer(Configuration.DefaultConfSettings.MAX_PEERS)
            val uriString = uriScheme + ":/" + senderAddress.toString()
            if (Neighbor.getNumPeers() < maxPeersAllowed) {
                log.info("Adding non-tethered neighbor: " + uriString)
                messageQ.publish("antn %s", uriString)
                try {
                    val uri = URI(uriString)
                    // 3rd parameter false (not tcp), 4th parameter true (configured tethering)
                    val newneighbor = newNeighbor(uri, false)
                    if (!neighbors.contains(newneighbor)) {
                        neighbors.add(newneighbor)
                        Neighbor.incNumPeers()
                    }
                } catch (e: URISyntaxException) {
                    log.error("Invalid URI string: " + uriString)
                }

            } else {
                if (rejectedAddresses.size > 20) {
                    // Avoid ever growing list in case of an attack.
                    rejectedAddresses.clear()
                } else if (rejectedAddresses.add(uriString)) {
                    messageQ.publish("rntn %s %s", uriString, maxPeersAllowed.toString())
                    log.info("Refused non-tethered neighbor: " + uriString +
                            " (max-peers = " + maxPeersAllowed.toString() + ")")
                }
            }
        }
    }

    fun addReceivedDataToReceiveQueue(receivedTransactionViewModel: TransactionViewModel, neighbor: Neighbor) {
        receiveQueue.add(ImmutablePair(receivedTransactionViewModel, neighbor))
        if (receiveQueue.size > RECV_QUEUE_SIZE) {
            receiveQueue.pollLast()
        }

    }

    fun addReceivedDataToReplyQueue(requestedHash: Hash, neighbor: Neighbor) {
        replyQueue.add(ImmutablePair(requestedHash, neighbor))
        if (replyQueue.size > REPLY_QUEUE_SIZE) {
            replyQueue.pollLast()
        }
    }


    fun processReceivedDataFromQueue() {
        val receivedData = receiveQueue.pollFirst()
        if (receivedData != null) {
            processReceivedData(receivedData.left, receivedData.right)
        }
    }

    fun replyToRequestFromQueue() {
        val receivedData = replyQueue.pollFirst()
        if (receivedData != null) {
            replyToRequest(receivedData.left, receivedData.right)
        }
    }

    fun processReceivedData(receivedTransactionViewModel: TransactionViewModel, neighbor: Neighbor) {

        var stored = false

        //store new transaction
        try {
            stored = receivedTransactionViewModel.store(tangle)
        } catch (e: Exception) {
            log.error("Error accessing persistence store.", e)
            neighbor.incInvalidTransactions()
        }

        //if new, then broadcast to all neighbors
        if (stored) {
            receivedTransactionViewModel.arrivalTime = System.currentTimeMillis()
            try {
                transactionValidator.updateStatus(receivedTransactionViewModel)
                receivedTransactionViewModel.updateSender(neighbor.address.toString())
                receivedTransactionViewModel.update(tangle, "arrivalTime|sender")
            } catch (e: Exception) {
                log.error("Error updating transactions.", e)
            }

            neighbor.incNewTransactions()
            broadcast(receivedTransactionViewModel)
        }

    }

    fun replyToRequest(requestedHash: Hash, neighbor: Neighbor) {

        var transactionViewModel: TransactionViewModel? = null
        val transactionPointer: Hash

        //retrieve requested transaction
        if (requestedHash == Hash.NULL_HASH) {
            //Random Tip Request
            try {
                if (transactionRequester.numberOfTransactionsToRequest() > 0 && rnd.nextDouble() < P_REPLY_RANDOM_TIP) {
                    neighbor.incRandomTransactionRequests()
                    transactionPointer = randomTipPointer
                    transactionViewModel = TransactionViewModel.fromHash(tangle, transactionPointer)
                } else {
                    //no tx to request, so no random tip will be sent as a reply.
                    return
                }
            } catch (e: Exception) {
                log.error("Error getting random tip.", e)
            }

        } else {
            //find requested trytes
            try {
                //transactionViewModel = TransactionViewModel.find(Arrays.copyOf(requestedHash.bytes(), TransactionRequester.REQUEST_HASH_SIZE));
                transactionViewModel = TransactionViewModel.fromHash(tangle, Hash(requestedHash.bytes(), 0, reqHashSize))
                //log.debug("Requested Hash: " + requestedHash + " \nFound: " + transactionViewModel.getHash());
            } catch (e: Exception) {
                log.error("Error while searching for transaction.", e)
            }

        }

        if (transactionViewModel != null && transactionViewModel.type == TransactionViewModel.FILLED_SLOT) {
            //send trytes back to neighbor
            try {
                sendPacket(sendingPacket, transactionViewModel, neighbor)

            } catch (e: Exception) {
                log.error("Error fetching transaction to request.", e)
            }

        } else {
            //trytes not found
            if (requestedHash != Hash.NULL_HASH && rnd.nextDouble() < P_PROPAGATE_REQUEST) {
                //request is an actual transaction and missing in request queue add it.
                try {
                    transactionRequester.requestTransaction(requestedHash, false)

                } catch (e: Exception) {
                    log.error("Error adding transaction to request.", e)
                }

            }
        }

    }

    @Throws(Exception::class)
    fun sendPacket(sendingPacket: DatagramPacket, transactionViewModel: TransactionViewModel, neighbor: Neighbor) {

        //limit amount of sends per second
        val now = System.currentTimeMillis()
        if (now - sendPacketsTimer.get() > 1000L) {
            //reset counter every second
            sendPacketsCounter.set(0)
            sendPacketsTimer.set(now)
        }
        if (sendLimit >= 0 && sendPacketsCounter.get() > sendLimit) {
            //if exceeded limit - don't send
            //log.info("exceeded limit - don't send - {}",sendPacketsCounter.get());
            return
        }

        synchronized(sendingPacket) {
            System.arraycopy(transactionViewModel.bytes, 0, sendingPacket.data, 0, TransactionViewModel.SIZE)
            val hash = transactionRequester.transactionToRequest(rnd.nextDouble() < P_SELECT_MILESTONE)
            System.arraycopy(hash?.bytes() ?: transactionViewModel.hash.bytes(), 0,
                    sendingPacket.data, TransactionViewModel.SIZE, reqHashSize)
            neighbor.send(sendingPacket)
        }

        sendPacketsCounter.getAndIncrement()
    }

    private fun spawnBroadcasterThread(): Runnable {
        return Runnable {

            log.info("Spawning Broadcaster Thread")

            while (!shuttingDown.get()) {

                try {
                    val transactionViewModel = broadcastQueue.pollFirst()
                    if (transactionViewModel != null) {

                        for (neighbor in neighbors) {
                            try {
                                sendPacket(sendingPacket, transactionViewModel, neighbor)
                            } catch (e: Exception) {
                                // ignore
                            }

                        }
                    }
                    Thread.sleep(PAUSE_BETWEEN_TRANSACTIONS.toLong())
                } catch (e: Exception) {
                    log.error("Broadcaster Thread Exception:", e)
                }

            }
            log.info("Shutting down Broadcaster Thread")
        }
    }

    private fun spawnTipRequesterThread(): Runnable {
        return Runnable {

            log.info("Spawning Tips Requester Thread")
            var lastTime: Long = 0
            while (!shuttingDown.get()) {

                try {
                    val transactionViewModel = TransactionViewModel.fromHash(tangle, milestone.latestMilestone)
                    System.arraycopy(transactionViewModel.bytes, 0, tipRequestingPacket.data, 0, TransactionViewModel.SIZE)
                    System.arraycopy(transactionViewModel.hash.bytes(), 0, tipRequestingPacket.data, TransactionViewModel.SIZE,
                            reqHashSize)
                    //Hash.SIZE_IN_BYTES);

                    neighbors.forEach { n -> n.send(tipRequestingPacket) }

                    val now = System.currentTimeMillis()
                    if (now - lastTime > 10000L) {
                        lastTime = now
                        messageQ.publish("rstat %d %d %d %d %d",
                                receiveQueueSize, broadcastQueueSize,
                                transactionRequester.numberOfTransactionsToRequest(), replyQueueSize,
                                TransactionViewModel.getNumberOfStoredTransactions(tangle))
                        log.info("toProcess = {} , toBroadcast = {} , toRequest = {} , toReply = {} / totalTransactions = {}",
                                receiveQueueSize, broadcastQueueSize,
                                transactionRequester.numberOfTransactionsToRequest(), replyQueueSize,
                                TransactionViewModel.getNumberOfStoredTransactions(tangle))
                    }

                    Thread.sleep(5000)
                } catch (e: Exception) {
                    log.error("Tips Requester Thread Exception:", e)
                }

            }
            log.info("Shutting down Requester Thread")
        }
    }

    private fun spawnProcessReceivedThread(): Runnable {
        return Runnable {

            log.info("Spawning Process Received Data Thread")

            while (!shuttingDown.get()) {

                try {
                    processReceivedDataFromQueue()
                    Thread.sleep(1)
                } catch (e: Exception) {
                    log.error("Process Received Data Thread Exception:", e)
                }

            }
            log.info("Shutting down Process Received Data Thread")
        }
    }

    private fun spawnReplyToRequestThread(): Runnable {
        return Runnable {

            log.info("Spawning Reply To Request Thread")

            while (!shuttingDown.get()) {

                try {
                    replyToRequestFromQueue()
                    Thread.sleep(1)
                } catch (e: Exception) {
                    log.error("Reply To Request Thread Exception:", e)
                }

            }
            log.info("Shutting down Reply To Request Thread")
        }
    }


    fun broadcast(transactionViewModel: TransactionViewModel) {
        broadcastQueue.add(transactionViewModel)
        if (broadcastQueue.size > BROADCAST_QUEUE_SIZE) {
            broadcastQueue.pollLast()
        }
    }

    @Throws(InterruptedException::class)
    fun shutdown() {
        shuttingDown.set(true)
        executor.awaitTermination(6, TimeUnit.SECONDS)
    }

    // helpers methods

    fun removeNeighbor(uri: URI, isConfigured: Boolean): Boolean {
        val neighbor = newNeighbor(uri, isConfigured)
        if (uri.scheme == "tcp") {
            neighbors.stream().filter { n -> n is TCPNeighbor }
                    .map { n -> n as TCPNeighbor }
                    .filter { n -> n == neighbor }
                    .forEach({ it.clear() })
        }
        return neighbors.remove(neighbor)
    }

    fun addNeighbor(neighbor: Neighbor): Boolean {
        return !neighbors.contains(neighbor) && neighbors.add(neighbor)
    }

    fun isUriValid(uri: URI?): Boolean {
        if (uri != null) {
            if (uri.scheme == "tcp" || uri.scheme == "udp") {
                if (InetSocketAddress(uri.host, uri.port).address != null) {
                    return true
                }
            }
            log.error("'{}' is not a valid uri schema or resolvable address.", uri)
            return false
        }
        log.error("Cannot read uri schema, please check neighbor config!")
        return false
    }

    fun newNeighbor(uri: URI, isConfigured: Boolean): Neighbor {
        if (isUriValid(uri)) {
            if (uri.scheme == "tcp") {
                return TCPNeighbor(InetSocketAddress(uri.host, uri.port), isConfigured)
            }
            if (uri.scheme == "udp") {
                return UDPNeighbor(InetSocketAddress(uri.host, uri.port), udpSocket!!, isConfigured)
            }
        }
        throw RuntimeException(uri.toString())
    }

    private fun parseNeighborsConfig() {
        val func = URIFunction()
        val ofunc = OURIFunction()
        Arrays.stream(configuration.string(Configuration.DefaultConfSettings.NEIGHBORS).split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()).distinct()
                .filter { s -> !s.isEmpty() }.map<Optional<URI>>(func).map<URI>(ofunc)
                .filter { u -> isUriValid(u) }
                .map { u -> newNeighbor(u, true) }
                .peek { u ->
                    log.info("-> Adding neighbor : {} ", u.address)
                    messageQ.publish("-> Adding Neighbor : %s", u.address)
                }.forEach({ neighbors.add(it) })
    }

    fun queuedTransactionsSize(): Int {
        return broadcastQueue.size
    }

    fun howManyNeighbors(): Int {
        return neighbors.size
    }

    inner class FIFOCache<K, V>(private val capacity: Int, private val dropRate: Double) {
        private val map: LinkedHashMap<K, V>
        private val rnd = SecureRandom()

        init {
            this.map = LinkedHashMap()
        }

        operator fun get(key: K): V? {
            val value = this.map[key]
            if (value != null && rnd.nextDouble() < this.dropRate) {
                this.map.remove(key)
                return null
            }
            return value
        }

        fun put(key: K, value: V?): V? {
            if (this.map.containsKey(key)) {
                return value
            }
            if (this.map.size >= this.capacity) {
                val it = this.map.keys.iterator()
                it.next()
                it.remove()
            }
            return this.map.put(key, value!!)
        }
    }

    companion object {

        private val log = LoggerFactory.getLogger(Node::class.java)
        private val PAUSE_BETWEEN_TRANSACTIONS = 1
        private var P_SELECT_MILESTONE: Double = 0.toDouble()
        private val rnd = SecureRandom()
        private val recentSeenBytesMissCount = AtomicLong(0L)
        private val recentSeenBytesHitCount = AtomicLong(0L)

        private var sendLimit: Long = -1
        private val sendPacketsCounter = AtomicLong(0L)
        private val sendPacketsTimer = AtomicLong(0L)

        val rejectedAddresses = ConcurrentSkipListSet<String>()


        private fun weightQueue(): ConcurrentSkipListSet<TransactionViewModel> {
            return ConcurrentSkipListSet { transaction1, transaction2 ->
                if (transaction1.weightMagnitude == transaction2.weightMagnitude) {
                    var i = Hash.SIZE_IN_BYTES
                    while (i-- > 0) {
                        if (transaction1.hash.bytes()[i] != transaction2.hash.bytes()[i]) {
                            transaction2.hash.bytes()[i] - transaction1.hash.bytes()[i]
                        }
                    }
                    0
                }
                transaction2.weightMagnitude - transaction1.weightMagnitude
            }
        }

        //TODO generalize these weightQueues
        private fun weightQueueHashPair(): ConcurrentSkipListSet<Pair<Hash, Neighbor>> {
            return ConcurrentSkipListSet { transaction1, transaction2 ->
                val tx1 = transaction1.left
                val tx2 = transaction2.left

                var i = Hash.SIZE_IN_BYTES
                while (i-- > 0) {
                    if (tx1.bytes()[i] != tx2.bytes()[i]) {
                        tx2.bytes()[i] - tx1.bytes()[i]
                    }
                }
                0
            }
        }

        private fun weightQueueTxPair(): ConcurrentSkipListSet<Pair<TransactionViewModel, Neighbor>> {
            return ConcurrentSkipListSet { transaction1, transaction2 ->
                val tx1 = transaction1.left
                val tx2 = transaction2.left

                if (tx1.weightMagnitude == tx2.weightMagnitude) {
                    var i = Hash.SIZE_IN_BYTES
                    while (i-- > 0) {
                        if (tx1.hash.bytes()[i] != tx2.hash.bytes()[i]) {
                            tx2.hash.bytes()[i] - tx1.hash.bytes()[i]
                        }
                    }
                    0
                }
                tx2.weightMagnitude - tx1.weightMagnitude
            }
        }

        fun uri(uri: String): Optional<URI> {
            try {
                return Optional.of(URI(uri))
            } catch (e: URISyntaxException) {
                log.error("Uri {} raised URI Syntax Exception", uri)
            }

            return Optional.empty()
        }
    }

}
