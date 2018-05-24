package com.iota.iri.utils

import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer

class TestSerializer {

    @Test
    fun testSerEndian() {
        val ltestvec = longArrayOf(0L, 1L, java.lang.Long.MAX_VALUE, 123456789L)
        val itestvec = intArrayOf(0, 1, Integer.MAX_VALUE, 123456789)

        for (l in ltestvec)
            Assert.assertArrayEquals(Serializer.serialize(l), bbSerialize(l))

        for (i in itestvec)
            Assert.assertArrayEquals(Serializer.serialize(i), bbSerialize(i))

    }

    companion object {

        // reference for original bytebuffer code
        fun bbSerialize(value: Long?): ByteArray {
            val buffer = ByteBuffer.allocate(java.lang.Long.BYTES)
            buffer.putLong(value!!)
            return buffer.array()
        }

        fun bbSerialize(integer: Int): ByteArray {
            val buffer = ByteBuffer.allocate(Integer.BYTES)
            buffer.putInt(integer)
            return buffer.array()
        }
    }
}
