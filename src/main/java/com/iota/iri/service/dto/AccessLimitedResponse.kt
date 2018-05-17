package com.iota.iri.service.dto

/**
 * Created by Adrian on 07.01.2017.
 */
class AccessLimitedResponse : AbstractResponse() {

    var error: String? = null
        private set

    companion object {

        fun create(error: String): AbstractResponse {
            val res = AccessLimitedResponse()
            res.error = error
            return res
        }
    }
}
