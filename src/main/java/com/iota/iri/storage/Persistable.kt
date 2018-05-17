package com.iota.iri.storage

import java.io.Serializable

/**
 * Created by paul on 5/6/17.
 */
interface Persistable : Serializable {
    fun bytes(): ByteArray
    fun read(bytes: ByteArray?)
    fun metadata(): ByteArray
    fun readMetadata(bytes: ByteArray)
    fun merge(): Boolean
}
