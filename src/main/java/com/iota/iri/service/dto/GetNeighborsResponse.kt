package com.iota.iri.service.dto

class GetNeighborsResponse : AbstractResponse() {

    internal lateinit var neighbors: Array<Neighbor?>
        private set

    internal class Neighbor {

        var address: String? = null
            private set
        var numberOfAllTransactions: Long = 0
        var numberOfRandomTransactionRequests: Long = 0
        var numberOfNewTransactions: Long = 0
        var numberOfInvalidTransactions: Long = 0
        var numberOfSentTransactions: Long = 0
        var connectionType: String = ""

        companion object {

            fun createFrom(n: com.iota.iri.network.Neighbor): Neighbor {
                val ne = Neighbor()
                val port = n.port
                ne.address = n.address.hostString + ":" + port
                ne.numberOfAllTransactions = n.numberOfAllTransactions
                ne.numberOfInvalidTransactions = n.numberOfInvalidTransactions
                ne.numberOfNewTransactions = n.numberOfNewTransactions
                ne.numberOfRandomTransactionRequests = n.numberOfRandomTransactionRequests
                ne.numberOfSentTransactions = n.numberOfSentTransactions
                ne.connectionType = n.connectionType()
                return ne
            }
        }
    }

    companion object {
        fun create(elements: List<com.iota.iri.network.Neighbor>): AbstractResponse {
            val response = GetNeighborsResponse()
            response.neighbors = arrayOfNulls(elements.size)
            var i = 0
            for (n in elements) {
                response.neighbors[i++] = Neighbor.createFrom(n)
            }
            return response
        }
    }
}
