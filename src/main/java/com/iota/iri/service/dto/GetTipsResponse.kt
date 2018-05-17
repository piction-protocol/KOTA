package com.iota.iri.service.dto

class GetTipsResponse : AbstractResponse() {

    var hashes: Array<String>? = null
        private set

    companion object {

        fun create(elements: List<String>): AbstractResponse {
            val res = GetTipsResponse()
            res.hashes = elements.toTypedArray()
            return res
        }
    }

}
