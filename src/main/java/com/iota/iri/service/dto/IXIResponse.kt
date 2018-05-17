package com.iota.iri.service.dto

/**
 * Created by paul on 2/10/17.
 */
class IXIResponse : AbstractResponse() {
    var response: Any? = null
        private set

    companion object {

        fun create(myixi: Any): IXIResponse {
            val ixiResponse = IXIResponse()
            ixiResponse.response = myixi
            return ixiResponse
        }
    }
}
