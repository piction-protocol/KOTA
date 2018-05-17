package com.iota.iri.service.dto

class wereAddressesSpentFrom : AbstractResponse() {

    var states: BooleanArray? = null
        private set

    companion object {

        fun create(inclusionStates: BooleanArray): AbstractResponse {
            val res = wereAddressesSpentFrom()
            res.states = inclusionStates
            return res
        }
    }
}
