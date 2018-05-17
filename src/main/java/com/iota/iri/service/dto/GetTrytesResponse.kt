package com.iota.iri.service.dto

class GetTrytesResponse : AbstractResponse() {

    var trytes: Array<String>? = null
        private set

    companion object {

        fun create(elements: List<String>): GetTrytesResponse {
            val res = GetTrytesResponse()
            res.trytes = elements.toTypedArray()
            return res
        }
    }
}
