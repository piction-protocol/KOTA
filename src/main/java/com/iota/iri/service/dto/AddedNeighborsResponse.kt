package com.iota.iri.service.dto

class AddedNeighborsResponse : AbstractResponse() {

    var addedNeighbors: Int = 0
        private set

    companion object {

        fun create(numberOfAddedNeighbors: Int): AbstractResponse {
            val res = AddedNeighborsResponse()
            res.addedNeighbors = numberOfAddedNeighbors
            return res
        }
    }
}
