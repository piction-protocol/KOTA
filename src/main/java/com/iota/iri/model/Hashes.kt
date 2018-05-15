package com.iota.iri.model

import com.iota.iri.compose.HashFunction
import com.iota.iri.storage.Persistable
import org.apache.commons.lang3.ArrayUtils
import java.util.*

/**
 * Created by paul on 3/8/17 for iri.
 */

open class Hashes : Persistable {
    var set: MutableSet<Hash> = LinkedHashSet()

    override fun bytes(): ByteArray {
        return set.parallelStream()
                .map<ByteArray>(HashFunction())
                .reduce { a, b -> ArrayUtils.addAll(ArrayUtils.add(a, delimiter), *b) }
                .orElse(ByteArray(0))
    }

    override fun read(bytes: ByteArray?) {
        if (bytes != null) {
            set = LinkedHashSet(bytes.size / (1 + Hash.SIZE_IN_BYTES) + 1)
            var i = 0
            while (i < bytes.size) {
                set.add(Hash(bytes, i, Hash.SIZE_IN_BYTES))
                i += 1 + Hash.SIZE_IN_BYTES
            }
        }
    }

    override fun metadata(): ByteArray {
        return ByteArray(0)
    }

    override fun readMetadata(bytes: ByteArray) {

    }

    override fun merge(): Boolean {
        return true
    }

    companion object {
        private val delimiter = ",".toByteArray()[0]
    }
}
