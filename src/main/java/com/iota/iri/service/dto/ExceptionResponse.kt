package com.iota.iri.service.dto

class ExceptionResponse : AbstractResponse() {

    var exception: String? = null
        private set

    companion object {

        fun create(exception: String): AbstractResponse {
            val res = ExceptionResponse()
            res.exception = exception
            return res
        }
    }
}
