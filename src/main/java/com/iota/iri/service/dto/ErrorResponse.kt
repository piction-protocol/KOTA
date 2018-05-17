package com.iota.iri.service.dto

class ErrorResponse : AbstractResponse() {

    var error: String? = null
        private set

    companion object {
        fun create(error: String): AbstractResponse {
            val res = ErrorResponse()
            res.error = error
            return res
        }
    }
}
