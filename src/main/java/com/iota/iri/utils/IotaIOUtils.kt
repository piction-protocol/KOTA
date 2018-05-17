package com.iota.iri.utils


import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory

class IotaIOUtils : IOUtils() {
    companion object {

        private val log = LoggerFactory.getLogger(IotaIOUtils::class.java)

        fun closeQuietly(vararg autoCloseables: AutoCloseable) {
            for (it in autoCloseables) {
                try {
                    it.close()
                } catch (ignored: Exception) {
                    log.debug("Silent exception occured", ignored)
                }

            }
        }
    }
}
