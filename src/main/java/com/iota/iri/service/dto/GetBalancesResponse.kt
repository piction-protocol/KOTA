package com.iota.iri.service.dto

class GetBalancesResponse : AbstractResponse() {

    var balances: List<String>? = null
        private set
    var references: List<String>? = null
        private set
    var milestoneIndex: Int = 0
        private set

    companion object {

        fun create(elements: List<String>, references: List<String>, milestoneIndex: Int): AbstractResponse {
            val res = GetBalancesResponse()
            res.balances = elements
            res.references = references
            res.milestoneIndex = milestoneIndex
            return res
        }
    }
}
