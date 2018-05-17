package com.iota.iri.service

interface CallableRequest<V> {
    fun call(request: Map<String, Any>): V
}
