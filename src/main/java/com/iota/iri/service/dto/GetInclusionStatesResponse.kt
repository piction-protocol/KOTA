package com.iota.iri.service.dto

class GetInclusionStatesResponse : AbstractResponse() {

    var states: BooleanArray? = null
        private set

    companion object {

        fun create(inclusionStates: BooleanArray): AbstractResponse {
            val res = GetInclusionStatesResponse()
            res.states = inclusionStates
            return res
        }
    }
}
