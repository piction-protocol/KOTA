package com.iota.iri.compose

import com.iota.iri.model.Hash

import java.util.function.Function

class HashFunction : Function<Hash, ByteArray> {
    override fun apply(hash: Hash): ByteArray {
        return hash.bytes()
    }
}
