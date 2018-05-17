package com.iota.iri.service.dto

import com.iota.iri.model.Hash

class GetNodeInfoResponse : AbstractResponse() {

    var appName: String? = null
        private set
    var appVersion: String? = null
        private set
    var jreAvailableProcessors: Int = 0
        private set
    var jreFreeMemory: Long = 0
        private set
    var jreVersion: String? = null
        private set

    var jreMaxMemory: Long = 0
        private set
    var jreTotalMemory: Long = 0
        private set
    var latestMilestone: String? = null
        private set
    var latestMilestoneIndex: Int = 0
        private set

    var latestSolidSubtangleMilestone: String? = null
        private set
    var latestSolidSubtangleMilestoneIndex: Int = 0
        private set

    var milestoneStartIndex: Int = 0
        private set

    var neighbors: Int = 0
        private set
    var packetsQueueSize: Int = 0
        private set
    var time: Long = 0
        private set
    var tips: Int = 0
        private set
    var transactionsToRequest: Int = 0
        private set

    companion object {

        fun create(appName: String, appVersion: String, jreAvailableProcessors: Int, jreFreeMemory: Long,
                   jreVersion: String, maxMemory: Long, totalMemory: Long, latestMilestone: Hash, latestMilestoneIndex: Int,
                   latestSolidSubtangleMilestone: Hash, latestSolidSubtangleMilestoneIndex: Int, milestoneStartIndex: Int,
                   neighbors: Int, packetsQueueSize: Int,
                   currentTimeMillis: Long, tips: Int, numberOfTransactionsToRequest: Int): AbstractResponse {
            val res = GetNodeInfoResponse()
            res.appName = appName
            res.appVersion = appVersion
            res.jreAvailableProcessors = jreAvailableProcessors
            res.jreFreeMemory = jreFreeMemory
            res.jreVersion = jreVersion

            res.jreMaxMemory = maxMemory
            res.jreTotalMemory = totalMemory
            res.latestMilestone = latestMilestone.toString()
            res.latestMilestoneIndex = latestMilestoneIndex

            res.latestSolidSubtangleMilestone = latestSolidSubtangleMilestone.toString()
            res.latestSolidSubtangleMilestoneIndex = latestSolidSubtangleMilestoneIndex

            res.milestoneStartIndex = milestoneStartIndex

            res.neighbors = neighbors
            res.packetsQueueSize = packetsQueueSize
            res.time = currentTimeMillis
            res.tips = tips
            res.transactionsToRequest = numberOfTransactionsToRequest
            return res
        }
    }
}
