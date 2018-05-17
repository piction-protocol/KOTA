package com.iota.iri.hash

interface Sponge {

    fun absorb(trits: IntArray, offset: Int, length: Int)
    fun squeeze(trits: IntArray, offset: Int, length: Int)
    fun reset()

    companion object {
        const val HASH_LENGTH = 243
    }
}
