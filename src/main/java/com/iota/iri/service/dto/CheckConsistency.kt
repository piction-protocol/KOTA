package com.iota.iri.service.dto

class CheckConsistency : AbstractResponse() {

    private var state: Boolean = false
    private var info: String? = null

    companion object {
        fun create(state: Boolean, info: String): AbstractResponse {
            val res = CheckConsistency()
            res.state = state
            res.info = info
            return res
        }
    }
}
