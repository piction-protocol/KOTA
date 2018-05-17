package com.iota.iri.service.dto

class AttachToTangleResponse : AbstractResponse() {

    var trytes: List<String>? = null
        private set

    companion object {

        fun create(elements: List<String>): AbstractResponse {
            val res = AttachToTangleResponse()
            res.trytes = elements
            return res
        }
    }
}
