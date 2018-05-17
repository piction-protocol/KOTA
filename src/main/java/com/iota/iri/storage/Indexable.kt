package com.iota.iri.storage

import java.io.Serializable

/**
 * Created by paul on 5/6/17.
 */
interface Indexable : Comparable<Indexable>, Serializable {
    fun bytes(): ByteArray
    fun read(bytes: ByteArray?)
    fun incremented(): Indexable
    fun decremented(): Indexable
}
