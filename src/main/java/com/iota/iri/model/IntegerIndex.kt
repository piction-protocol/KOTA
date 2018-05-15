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

    override fun compareTo(o: Indexable): Int {
        val i = IntegerIndex(Serializer.getInteger(o.bytes()))
        return value - (o as IntegerIndex).value
    }
}
