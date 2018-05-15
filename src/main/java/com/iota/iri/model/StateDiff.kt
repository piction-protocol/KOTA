package com.iota.iri.model

import com.iota.iri.storage.Persistable
import com.iota.iri.utils.Serializer
import org.apache.commons.lang3.ArrayUtils

import java.util.Arrays
import java.util.HashMap
import java.util.function.BinaryOperator

/**
 * Created by paul on 5/6/17.
 */
class StateDiff : Persistable {
    var state: MutableMap<Hash, Long> = HashMap()

    override fun bytes(): ByteArray {
        return state.entries.parallelStream()
                .map { entry -> ArrayUtils.addAll(entry.key.bytes(), *Serializer.serialize(entry.value)) }
                .reduce({ array1, array2 ->
                    ArrayUtils.addAll(array1)
                    ArrayUtils.addAll(array2)
                })
                .orElse(ByteArray(0))
    }

    override fun read(bytes: ByteArray?) {
        var i = 0
        state = HashMap()
        bytes?.let {
            while (i < bytes.size) {
                state.put(Hash(bytes, i, Hash.SIZE_IN_BYTES),
                        Serializer.getLong(Arrays.copyOfRange(bytes, i + Hash.SIZE_IN_BYTES, i + Hash.SIZE_IN_BYTES + java.lang.Long.BYTES)))
                i += Hash.SIZE_IN_BYTES + java.lang.Long.BYTES
            }
        }
    }

    override fun metadata(): ByteArray {
        return ByteArray(0)
    }

    override fun readMetadata(bytes: ByteArray) {}

    override fun merge(): Boolean {
        return false
    }
}