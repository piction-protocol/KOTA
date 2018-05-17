package com.iota.iri.service.dto

class FindTransactionsResponse : AbstractResponse() {

    var hashes: Array<String>? = null
        private set

    companion object {

        fun create(elements: List<String>): AbstractResponse {
            val res = FindTransactionsResponse()
            res.hashes = elements.toTypedArray()
            return res
        }
    }
}
