package com.iota.iri.conf

import org.ini4j.Ini
import org.ini4j.IniPreferences
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences

/**
 * All those settings are modificable at runtime,
 * but for most of them the node needs to be restarted.
 */

class Configuration {
    private var ini: Ini? = null
    private var prefs: Preferences? = null

    private val log = LoggerFactory.getLogger(Configuration::class.java)

    private val conf = ConcurrentHashMap<String, String>()

    enum class DefaultConfSettings {
        CONFIG,
        PORT,
        API_HOST,
        UDP_RECEIVER_PORT,
        TCP_RECEIVER_PORT,
        TESTNET,
        DEBUG,
        REMOTE_LIMIT_API,
        REMOTE_AUTH,
        NEIGHBORS,
        IXI_DIR,
        DB_PATH,
        DB_LOG_PATH,
        DB_CACHE_SIZE,
        P_REMOVE_REQUEST,
        P_DROP_TRANSACTION,
        P_SELECT_MILESTONE_CHILD,
        P_SEND_MILESTONE,
        P_REPLY_RANDOM_TIP,
        P_PROPAGATE_REQUEST,
        MAIN_DB, EXPORT, // exports transaction trytes to filesystem
        SEND_LIMIT,
        MAX_PEERS,
        DNS_RESOLUTION_ENABLED,
        DNS_REFRESHER_ENABLED,
        COORDINATOR,
        DONT_VALIDATE_TESTNET_MILESTONE_SIG,
        REVALIDATE,
        RESCAN_DB,
        MIN_RANDOM_WALKS,
        MAX_RANDOM_WALKS,
        MAX_FIND_TRANSACTIONS,
        MAX_REQUESTS_LIST,
        MAX_GET_TRYTES,
        MAX_BODY_LENGTH,
        MAX_DEPTH,
        MWM,
        ZMQ_ENABLED,
        ZMQ_PORT,
        ZMQ_IPC,
        ZMQ_THREADS,
        Q_SIZE_NODE,
        P_DROP_CACHE_ENTRY,
        CACHE_SIZE_BYTES,
        SNAPSHOT_FILE,
        SNAPSHOT_SIGNATURE_FILE,
        MILESTONE_START_INDEX,
        NUMBER_OF_KEYS_IN_A_MILESTONE,
        TRANSACTION_PACKET_SIZE,
        REQUEST_HASH_SIZE,
        SNAPSHOT_TIME
    }


    init {
        conf.put(DefaultConfSettings.PORT.name, "14600")
        conf.put(DefaultConfSettings.API_HOST.name, "localhost")
        conf.put(DefaultConfSettings.UDP_RECEIVER_PORT.name, "14600")
        conf.put(DefaultConfSettings.TCP_RECEIVER_PORT.name, "15600")
        conf.put(DefaultConfSettings.TESTNET.name, "false")
        conf.put(DefaultConfSettings.DEBUG.name, "false")
        conf.put(DefaultConfSettings.REMOTE_LIMIT_API.name, "")
        conf.put(DefaultConfSettings.REMOTE_AUTH.name, "")
        conf.put(DefaultConfSettings.NEIGHBORS.name, "")
        conf.put(DefaultConfSettings.IXI_DIR.name, "ixi")
        conf.put(DefaultConfSettings.DB_PATH.name, "mainnetdb")
        conf.put(DefaultConfSettings.DB_LOG_PATH.name, "mainnet.log")
        conf.put(DefaultConfSettings.DB_CACHE_SIZE.name, "100000") //KB
        conf.put(DefaultConfSettings.CONFIG.name, "iota.ini")
        conf.put(DefaultConfSettings.P_REMOVE_REQUEST.name, "0.01")
        conf.put(DefaultConfSettings.P_DROP_TRANSACTION.name, "0.0")
        conf.put(DefaultConfSettings.P_SELECT_MILESTONE_CHILD.name, "0.7")
        conf.put(DefaultConfSettings.P_SEND_MILESTONE.name, "0.02")
        conf.put(DefaultConfSettings.P_REPLY_RANDOM_TIP.name, "0.66")
        conf.put(DefaultConfSettings.P_PROPAGATE_REQUEST.name, "0.01")
        conf.put(DefaultConfSettings.MAIN_DB.name, "rocksdb")
        conf.put(DefaultConfSettings.EXPORT.name, "false")
        conf.put(DefaultConfSettings.SEND_LIMIT.name, "-1.0")
        conf.put(DefaultConfSettings.MAX_PEERS.name, "0")
        conf.put(DefaultConfSettings.DNS_REFRESHER_ENABLED.name, "true")
        conf.put(DefaultConfSettings.DNS_RESOLUTION_ENABLED.name, "true")
        conf.put(DefaultConfSettings.REVALIDATE.name, "false")
        conf.put(DefaultConfSettings.RESCAN_DB.name, "false")
        conf.put(DefaultConfSettings.MWM.name, MAINNET_MWM)

        // Pick a number based on best performance
        conf.put(DefaultConfSettings.MIN_RANDOM_WALKS.name, "5")
        conf.put(DefaultConfSettings.MAX_RANDOM_WALKS.name, "27")
        // Pick a milestone depth number depending on risk model
        conf.put(DefaultConfSettings.MAX_DEPTH.name, "15")

        conf.put(DefaultConfSettings.MAX_FIND_TRANSACTIONS.name, "100000")
        conf.put(DefaultConfSettings.MAX_REQUESTS_LIST.name, "1000")
        conf.put(DefaultConfSettings.MAX_GET_TRYTES.name, "10000")
        conf.put(DefaultConfSettings.MAX_BODY_LENGTH.name, "1000000")
        conf.put(DefaultConfSettings.ZMQ_ENABLED.name, "false")
        conf.put(DefaultConfSettings.ZMQ_PORT.name, "5556")
        conf.put(DefaultConfSettings.ZMQ_IPC.name, "ipc://iri")
        conf.put(DefaultConfSettings.ZMQ_THREADS.name, "2")

        conf.put(DefaultConfSettings.Q_SIZE_NODE.name, "1000")
        conf.put(DefaultConfSettings.P_DROP_CACHE_ENTRY.name, "0.02")
        conf.put(DefaultConfSettings.CACHE_SIZE_BYTES.name, "15000")

        conf.put(DefaultConfSettings.COORDINATOR.name, MAINNET_COORDINATOR_ADDRESS)
        conf.put(DefaultConfSettings.DONT_VALIDATE_TESTNET_MILESTONE_SIG.name, "false")
        conf.put(DefaultConfSettings.SNAPSHOT_FILE.name, MAINNET_SNAPSHOT_FILE)
        conf.put(DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE.name, MAINNET_SNAPSHOT_SIG_FILE)
        conf.put(DefaultConfSettings.MILESTONE_START_INDEX.name, MAINNET_MILESTONE_START_INDEX)
        conf.put(DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE.name, MAINNET_NUM_KEYS_IN_MILESTONE)
        conf.put(DefaultConfSettings.TRANSACTION_PACKET_SIZE.name, PACKET_SIZE)
        conf.put(DefaultConfSettings.REQUEST_HASH_SIZE.name, REQ_HASH_SIZE)
        conf.put(DefaultConfSettings.SNAPSHOT_TIME.name, GLOBAL_SNAPSHOT_TIME)
    }

    @Throws(IOException::class)
    fun init(): Boolean {
        val confFile = File(string(Configuration.DefaultConfSettings.CONFIG))
        if (confFile.exists()) {
            ini = Ini(confFile)
            prefs = IniPreferences(ini)
            return true
        }
        return false
    }

    fun getIniValue(k: String): String? {
        return ini?.let { prefs!!.node("IRI").get(k, null) }
    }

    private fun getConfValue(k: String): String {
        val value = getIniValue(k)
        return value ?: conf[k]!!
    }

    fun allSettings(): String {
        val settings = StringBuilder()
        conf.keys.forEach { t -> settings.append("Set '").append(t).append("'\t -> ").append(getConfValue(t)).append("\n") }
        return settings.toString()
    }

    fun put(k: String, v: String) {
        log.debug("Setting {} with {}", k, v)
        conf.put(k, v)
    }

    fun put(d: DefaultConfSettings, v: String) {
        log.debug("Setting {} with {}", d.name, v)
        conf.put(d.name, v)
    }

    private fun string(k: String): String {
        return getConfValue(k)
    }

    fun floating(k: String): Float {
        return java.lang.Float.parseFloat(getConfValue(k))
    }

    fun doubling(k: String): Double {
        return java.lang.Double.parseDouble(getConfValue(k))
    }

    private fun integer(k: String): Int {
        return Integer.parseInt(getConfValue(k))
    }

    private fun booling(k: String): Boolean {
        return java.lang.Boolean.parseBoolean(getConfValue(k))
    }

    private fun longNum(k: String): Long {
        return java.lang.Long.parseLong(getConfValue(k))
    }

    fun string(d: DefaultConfSettings): String {
        return string(d.name)
    }

    fun integer(d: DefaultConfSettings): Int {
        return integer(d.name)
    }

    fun longNum(d: DefaultConfSettings): Long {
        return longNum(d.name)
    }

    fun booling(d: DefaultConfSettings): Boolean {
        return booling(d.name)
    }

    companion object {

        val MAINNET_COORDINATOR_ADDRESS = "KPWCHICGJZXKE9GSUDXZYUAPLHAKAHYHDXNPHENTERYMMBQOPSQIDENXKLKCEYCPVTZQLEEJVYJZV9BWU"
        val TESTNET_COORDINATOR_ADDRESS = "EQQFCZBIHRHWPXKMTOLMYUYPCN9XLMJPYZVFJSAY9FQHCCLWTOLLUGKKMXYFDBOOYFBLBI9WUEILGECYM"
        val MAINNET_SNAPSHOT_FILE = "/snapshotMainnet.txt"
        val TESTNET_SNAPSHOT_FILE = "/snapshotTestnet.txt"
        val MAINNET_SNAPSHOT_SIG_FILE = "/snapshotMainnet.sig"

        val PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT = "/previousEpochsSpentAddresses.txt"
        val PREVIOUS_EPOCH_SPENT_ADDRESSES_SIG = "/previousEpochsSpentAddresses.sig"
        val MAINNET_MILESTONE_START_INDEX = "426550"
        val TESTNET_MILESTONE_START_INDEX = "434525"
        val MAINNET_NUM_KEYS_IN_MILESTONE = "20"
        val TESTNET_NUM_KEYS_IN_MILESTONE = "22"
        val GLOBAL_SNAPSHOT_TIME = "1525042800"
        val TESTNET_GLOBAL_SNAPSHOT_TIME = "1522306500"

        val MAINNET_MWM = "14"
        val TESTNET_MWM = "9"
        val PACKET_SIZE = "1650"
        val TESTNET_PACKET_SIZE = "1653"
        val REQ_HASH_SIZE = "46"
        val TESTNET_REQ_HASH_SIZE = "49"
    }
}
