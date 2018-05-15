package com.iota.iri.model

import com.iota.iri.storage.Persistable
import com.iota.iri.utils.Serializer
import org.apache.commons.lang3.ArrayUtils

import java.io.Serializable
import java.nio.ByteBuffer

/**
 * Created by paul on 4/11/17.
 */
class Milestone : Persistable {
    lateinit var index: IntegerIndex
    lateinit var hash: Hash

    override fun bytes(): ByteArray {
        return ArrayUtils.addAll(index.bytes(), *hash.bytes())
    }

    override fun read(bytes: ByteArray?) {
        bytes?.let {
            index = IntegerIndex(Serializer.getInteger(bytes))
            hash = Hash(bytes, Integer.BYTES, Hash.SIZE_IN_BYTES)
        }
    }

    override fun metadata(): ByteArray {
        return ByteArray(0)
    }

    override fun readMetadata(bytes: ByteArray) {

    }

    override fun merge(): Boolean {
        return false
    }
}
