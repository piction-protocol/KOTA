package com.iota.iri.model

import com.iota.iri.storage.Indexable
import com.iota.iri.utils.Serializer

/**
 * Created by paul on 5/6/17.
 */

class IntegerIndex(var value: Int) : Indexable {

    override fun bytes(): ByteArray {
        return Serializer.serialize(value)
    }

    override fun read(bytes: ByteArray) {
        this.value = Serializer.getInteger(bytes)
    }

    override fun incremented(): Indexable {
        return IntegerIndex(value + 1)
    }

    override fun decremented(): Indexable {
        return IntegerIndex(value - 1)
    }

    override fun compareTo(index: Indexable): Int {
        IntegerIndex(Serializer.getInteger(index.bytes()))
        return value - (index as IntegerIndex).value
    }
}
