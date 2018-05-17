package com.iota.iri

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.util.StatusPrinter
import com.iota.iri.conf.Configuration
import com.iota.iri.conf.Configuration.DefaultConfSettings
import com.iota.iri.service.API
import com.sanityinc.jargs.CmdLineParser
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Main IOTA Reference Implementation starting class
 */
object IRI {

    private val log = LoggerFactory.getLogger(IRI::class.java)

    val MAINNET_NAME = "IRI"
    val TESTNET_NAME = "IRI Testnet"
    val VERSION = "1.4.2.4"
    lateinit var iota: Iota
    lateinit var api: API
    lateinit var ixi: IXI
    lateinit var configuration: Configuration
    private val TESTNET_FLAG_REQUIRED = "--testnet flag must be turned on to use "

    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        configuration = Configuration()
        validateParams(configuration, args)
        log.info("Welcome to {} {}", if (configuration.booling(DefaultConfSettings.TESTNET)) TESTNET_NAME else MAINNET_NAME, VERSION)
        iota = Iota(configuration)
        ixi = IXI(iota)
        api = API(iota, ixi)
        shutdownHook()

        if (configuration.booling(DefaultConfSettings.DEBUG)) {
            log.info("You have set the debug flag. To enable debug output, you need to uncomment the DEBUG appender in the source tree at iri/src/main/resources/logback.xml and re-package iri.jar")
        }

        if (configuration.booling(DefaultConfSettings.EXPORT)) {
            var exportDir = File("export")
            // if the directory does not exist, create it
            if (!exportDir.exists()) {
                log.info("Create directory 'export'")
                try {
                    exportDir.mkdir()
                } catch (e: SecurityException) {
                    log.error("Could not create directory", e)
                }

            }
            exportDir = File("export-solid")
            // if the directory does not exist, create it
            if (!exportDir.exists()) {
                log.info("Create directory 'export-solid'")
                try {
                    exportDir.mkdir()
                } catch (e: SecurityException) {
                    log.error("Could not create directory", e)
                }

            }
        }

        try {
            iota.init()
            api.init()
            ixi.init(configuration.string(Configuration.DefaultConfSettings.IXI_DIR))
        } catch (e: Exception) {
            log.error("Exception during IOTA node initialisation: ", e)
            System.exit(-1)
        }

        log.info("IOTA Node initialised correctly.")
    }

    @Throws(IOException::class)
    fun validateParams(configuration: Configuration, args: Array<String>?) {

        val configurationInit = configuration.init()

        if (args == null || args.size < 2 && !configurationInit) {
            log.error("Invalid arguments list. Provide ini-file 'iota.ini' or API port number (i.e. '-p 14600').")
            printUsage()
        }

        val parser = CmdLineParser()

        val config = parser.addStringOption('c', "config")
        val port = parser.addStringOption('p', "port")
        val rportudp = parser.addStringOption('u', "udp-receiver-port")
        val rporttcp = parser.addStringOption('t', "tcp-receiver-port")
        val debug = parser.addBooleanOption('d', "debug")
        val remote = parser.addBooleanOption("remote")
        val remoteLimitApi = parser.addStringOption("remote-limit-api")
        val remoteAuth = parser.addStringOption("remote-auth")
        val neighbors = parser.addStringOption('n', "neighbors")
        val export = parser.addBooleanOption("export")
        val zmq = parser.addBooleanOption("zmq-enabled")
        val help = parser.addBooleanOption('h', "help")
        val testnet = parser.addBooleanOption("testnet")
        val revalidate = parser.addBooleanOption("revalidate")
        val rescan = parser.addBooleanOption("rescan")
        val sendLimit = parser.addStringOption("send-limit")
        val sync = parser.addBooleanOption("sync")
        val dnsResolutionFalse = parser.addBooleanOption("dns-resolution-false")
        val maxPeers = parser.addStringOption("max-peers")
        val testnetCoordinator = parser.addStringOption("testnet-coordinator")
        val disableCooValidation = parser.addBooleanOption("testnet-no-coo-validation")
        val snapshot = parser.addStringOption("snapshot")
        val snapshotSignature = parser.addStringOption("snapshot-sig")
        val minimalWeightMagnitude = parser.addIntegerOption("mwm")
        val milestoneStartIndex = parser.addIntegerOption("milestone-start")
        val milestoneKeys = parser.addIntegerOption("milestone-keys")
        val snapshotTime = parser.addLongOption("snapshot-timestamp")


        try {
            assert(args != null)
            parser.parse(args!!)
        } catch (e: CmdLineParser.OptionException) {
            log.error("CLI error: ", e)
            printUsage()
            System.exit(2)
        }

        // optional config file path
        val confFilePath = parser.getOptionValue(config)
        if (confFilePath != null) {
            configuration.put(DefaultConfSettings.CONFIG, confFilePath)
            configuration.init()
        }

        //This block cannot be moved down
        val isTestnet = Optional.ofNullable(parser.getOptionValue(testnet)).orElse(java.lang.Boolean.FALSE) || configuration.booling(DefaultConfSettings.TESTNET)
        if (isTestnet) {
            configuration.put(DefaultConfSettings.TESTNET, "true")
            configuration.put(DefaultConfSettings.DB_PATH.name, "testnetdb")
            configuration.put(DefaultConfSettings.DB_LOG_PATH.name, "testnetdb.log")
            configuration.put(DefaultConfSettings.COORDINATOR, Configuration.TESTNET_COORDINATOR_ADDRESS)
            configuration.put(DefaultConfSettings.SNAPSHOT_FILE, Configuration.TESTNET_SNAPSHOT_FILE)
            configuration.put(DefaultConfSettings.MILESTONE_START_INDEX, Configuration.TESTNET_MILESTONE_START_INDEX)
            configuration.put(DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE, "")
            configuration.put(DefaultConfSettings.MWM, Configuration.TESTNET_MWM)
            configuration.put(DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE,
                    Configuration.TESTNET_NUM_KEYS_IN_MILESTONE)
            configuration.put(DefaultConfSettings.TRANSACTION_PACKET_SIZE, Configuration.TESTNET_PACKET_SIZE)
            configuration.put(DefaultConfSettings.REQUEST_HASH_SIZE, Configuration.TESTNET_REQ_HASH_SIZE)
            configuration.put(DefaultConfSettings.SNAPSHOT_TIME, Configuration.TESTNET_GLOBAL_SNAPSHOT_TIME)
        }

        // mandatory args
        val inicport = configuration.getIniValue(DefaultConfSettings.PORT.name)
        val cport = inicport ?: parser.getOptionValue(port)
        if (cport == null) {
            log.error("Invalid arguments list. Provide at least the PORT in iota.ini or with -p option")
            printUsage()
        } else {
            configuration.put(DefaultConfSettings.PORT, cport)
        }

        // optional flags
        if (parser.getOptionValue(help) != null) {
            printUsage()
        }


        var cns: String? = parser.getOptionValue(neighbors)
        if (cns == null) {
            log.warn("No neighbor has been specified. Server starting nodeless.")
            cns = StringUtils.EMPTY
        }
        configuration.put(DefaultConfSettings.NEIGHBORS, cns)

        val vremoteapilimit = parser.getOptionValue(remoteLimitApi)
        if (vremoteapilimit != null) {
            log.debug("The following api calls are not allowed : {} ", vremoteapilimit)
            configuration.put(DefaultConfSettings.REMOTE_LIMIT_API, vremoteapilimit)
        }

        val vremoteauth = parser.getOptionValue(remoteAuth)
        if (vremoteauth != null) {
            log.debug("Remote access requires basic authentication")
            configuration.put(DefaultConfSettings.REMOTE_AUTH, vremoteauth)
        }

        val vrportudp = parser.getOptionValue(rportudp)
        if (vrportudp != null) {
            configuration.put(DefaultConfSettings.UDP_RECEIVER_PORT, vrportudp)
        }

        val vrporttcp = parser.getOptionValue(rporttcp)
        if (vrporttcp != null) {
            configuration.put(DefaultConfSettings.TCP_RECEIVER_PORT, vrporttcp)
        }

        if (parser.getOptionValue(remote) != null) {
            log.info("Remote access enabled. Binding API socket to listen any interface.")
            configuration.put(DefaultConfSettings.API_HOST, "0.0.0.0")
        }

        if (parser.getOptionValue(export) != null) {
            log.info("Export transaction trytes turned on.")
            configuration.put(DefaultConfSettings.EXPORT, "true")
        }

        if (parser.getOptionValue(zmq) != null) {
            configuration.put(DefaultConfSettings.ZMQ_ENABLED, "true")
        }

        if (Integer.parseInt(cport!!) < 1024) {
            log.warn("Warning: api port value seems too low.")
        }

        if (parser.getOptionValue(debug) != null) {
            configuration.put(DefaultConfSettings.DEBUG, "true")
            log.info(configuration.allSettings())
            StatusPrinter.print(LoggerFactory.getILoggerFactory() as LoggerContext)
        }


        val coordinatorAddress = parser.getOptionValue(testnetCoordinator)
        if (coordinatorAddress != null) {
            if (isTestnet) {
                configuration.put(DefaultConfSettings.COORDINATOR, coordinatorAddress)
            } else {
                log.warn(TESTNET_FLAG_REQUIRED + testnetCoordinator.longForm())
            }
        }

        val noCooValidation = parser.getOptionValue(disableCooValidation)
        if (noCooValidation != null) {
            if (isTestnet) {
                configuration.put(DefaultConfSettings.DONT_VALIDATE_TESTNET_MILESTONE_SIG, noCooValidation.toString())
            } else {
                log.warn(TESTNET_FLAG_REQUIRED + noCooValidation)
            }
        }

        //TODO check what happens if string is invalid int
        val mwm = parser.getOptionValue(minimalWeightMagnitude)
        if (mwm != null) {
            configuration.put(DefaultConfSettings.MWM, mwm.toString())
        }

        val snapshotFile = parser.getOptionValue(snapshot)
        if (snapshotFile != null) {
            configuration.put(DefaultConfSettings.SNAPSHOT_FILE, snapshotFile)
        }

        val snapshotSig = parser.getOptionValue(snapshotSignature)
        if (snapshotSig != null) {
            configuration.put(DefaultConfSettings.SNAPSHOT_SIGNATURE_FILE, snapshotSig)
        }

        val milestoneStart = parser.getOptionValue(milestoneStartIndex)
        if (milestoneStart != null) {
            configuration.put(DefaultConfSettings.MILESTONE_START_INDEX, milestoneStart.toString())
        }

        val numberOfKeys = parser.getOptionValue(milestoneKeys)
        if (numberOfKeys != null) {
            configuration.put(DefaultConfSettings.NUMBER_OF_KEYS_IN_A_MILESTONE, numberOfKeys.toString())
        }

        val snapshotTimestamp = parser.getOptionValue(snapshotTime)
        if (snapshotTimestamp != null) {
            configuration.put(DefaultConfSettings.SNAPSHOT_TIME, snapshotTimestamp.toString())
        }

        if (parser.getOptionValue(revalidate) != null) {
            configuration.put(DefaultConfSettings.REVALIDATE, "true")
        }

        if (parser.getOptionValue(rescan) != null) {
            configuration.put(DefaultConfSettings.RESCAN_DB, "true")
        }

        if (parser.getOptionValue(dnsResolutionFalse) != null) {
            configuration.put(DefaultConfSettings.DNS_RESOLUTION_ENABLED, "false")
        }


        val vsendLimit = parser.getOptionValue(sendLimit)
        if (vsendLimit != null) {
            configuration.put(DefaultConfSettings.SEND_LIMIT, vsendLimit)
        }

        val vmaxPeers = parser.getOptionValue(maxPeers)
        if (vmaxPeers != null) {
            configuration.put(DefaultConfSettings.MAX_PEERS, vmaxPeers)
        }
    }

    private fun printUsage() {
        log.info("Usage: java -jar {}-{}.jar " +
                "[{-n,--neighbors} '<list of neighbors>'] " +
                "[{-p,--port} 14600] " +
                "[{-c,--config} 'config-file-name'] " +
                "[{-u,--udp-receiver-port} 14600] " +
                "[{-t,--tcp-receiver-port} 15600] " +
                "[{-d,--debug} false] " +
                "[{--testnet} false]" +
                "[{--remote} false]" +
                "[{--remote-auth} string]" +
                "[{--remote-limit-api} string]", MAINNET_NAME, VERSION)
        System.exit(0)
    }

    private fun shutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread({

            log.info("Shutting down IOTA node, please hold tight...")
            try {
                ixi.shutdown()
                api.shutDown()
                iota.shutdown()
            } catch (e: Exception) {
                log.error("Exception occurred shutting down IOTA node: ", e)
            }
        }, "Shutdown Hook"))
    }
}
