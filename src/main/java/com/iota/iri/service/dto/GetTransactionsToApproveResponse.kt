package com.iota.iri.service.dto

import com.iota.iri.model.Hash

class GetTransactionsToApproveResponse : AbstractResponse() {

    var trunkTransaction: String? = null
        private set
    var branchTransaction: String? = null
        private set

    companion object {

        fun create(trunkTransactionToApprove: Hash, branchTransactionToApprove: Hash): AbstractResponse {
            val res = GetTransactionsToApproveResponse()
            res.trunkTransaction = trunkTransactionToApprove.toString()
            res.branchTransaction = branchTransactionToApprove.toString()
            return res
        }
    }
}
