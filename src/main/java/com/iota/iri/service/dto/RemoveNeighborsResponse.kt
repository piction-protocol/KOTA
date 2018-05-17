package com.iota.iri.service.dto

class RemoveNeighborsResponse : AbstractResponse() {

    var removedNeighbors: Int = 0
        private set

    companion object {

        fun create(numberOfRemovedNeighbors: Int): AbstractResponse {
            val res = RemoveNeighborsResponse()
            res.removedNeighbors = numberOfRemovedNeighbors
            return res
        }
    }
}
