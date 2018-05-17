package com.iota.iri.model

import com.iota.iri.controllers.TransactionViewModel
import com.iota.iri.storage.Persistable
import com.iota.iri.utils.Serializer

import java.nio.ByteBuffer

/**
 * Created by paul on 3/2/17 for iri.
 */

class Transaction : Persistable {

    lateinit var bytes: ByteArray

    lateinit var address: Hash
    lateinit var bundle: Hash
    lateinit var trunk: Hash
    lateinit var branch: Hash
    lateinit var obsoleteTag: Hash

    var value: Long = 0
    var currentIndex: Long = 0
    var lastIndex: Long = 0
    var timestamp: Long = 0

    lateinit var tag: Hash
    var attachmentTimestamp: Long = 0
    var attachmentTimestampLowerBound: Long = 0
    var attachmentTimestampUpperBound: Long = 0

    var validity = 0
    var type = TransactionViewModel.PREFILLED_SLOT
    var arrivalTime: Long = 0

    //public boolean confirmed = false;
    var parsed = false
    var solid = false
    var height: Long = 0
    var sender = ""
    var snapshot: Int = 0

    override fun bytes(): ByteArray {
        return bytes
    }

    override fun read(bytes: ByteArray?) {
        bytes?.let {
            this.bytes = ByteArray(SIZE)
            System.arraycopy(bytes, 0, this.bytes, 0, SIZE)
            this.type = TransactionViewModel.FILLED_SLOT
        }
    }

    override fun metadata(): ByteArray {
        val allocateSize = Hash.SIZE_IN_BYTES * 6 + //address,bundle,trunk,branch,obsoleteTag,tag
                java.lang.Long.BYTES * 9 + //value,currentIndex,lastIndex,timestamp,attachmentTimestampLowerBound,attachmentTimestampUpperBound,arrivalTime,height
                Integer.BYTES * 3 + //validity,type,snapshot
                1 + //solid
                sender.toByteArray().size //sender
        val buffer = ByteBuffer.allocate(allocateSize)
        buffer.put(address.bytes())
        buffer.put(bundle.bytes())
        buffer.put(trunk.bytes())
        buffer.put(branch.bytes())
        buffer.put(obsoleteTag.bytes())
        buffer.put(Serializer.serialize(value))
        buffer.put(Serializer.serialize(currentIndex))
        buffer.put(Serializer.serialize(lastIndex))
        buffer.put(Serializer.serialize(timestamp))

        buffer.put(tag.bytes())
        buffer.put(Serializer.serialize(attachmentTimestamp))
        buffer.put(Serializer.serialize(attachmentTimestampLowerBound))
        buffer.put(Serializer.serialize(attachmentTimestampUpperBound))

        buffer.put(Serializer.serialize(validity))
        buffer.put(Serializer.serialize(type))
        buffer.put(Serializer.serialize(arrivalTime))
        buffer.put(Serializer.serialize(height))
        //buffer.put((byte) (confirmed ? 1:0));
        buffer.put((if (solid) 1 else 0).toByte())
        buffer.put(Serializer.serialize(snapshot))
        buffer.put(sender.toByteArray())
        return buffer.array()
    }

    override fun readMetadata(bytes: ByteArray) {
        var i = 0
        bytes?.let {
            address = Hash(bytes, i, Hash.SIZE_IN_BYTES)
            i += Hash.SIZE_IN_BYTES
            bundle = Hash(bytes, i, Hash.SIZE_IN_BYTES)
            i += Hash.SIZE_IN_BYTES
            trunk = Hash(bytes, i, Hash.SIZE_IN_BYTES)
            i += Hash.SIZE_IN_BYTES
            branch = Hash(bytes, i, Hash.SIZE_IN_BYTES)
            i += Hash.SIZE_IN_BYTES
            obsoleteTag = Hash(bytes, i, Hash.SIZE_IN_BYTES)
            i += Hash.SIZE_IN_BYTES
            value = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            currentIndex = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            lastIndex = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            timestamp = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES

            tag = Hash(bytes, i, Hash.SIZE_IN_BYTES)
            i += Hash.SIZE_IN_BYTES
            attachmentTimestamp = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            attachmentTimestampLowerBound = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            attachmentTimestampUpperBound = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES

            validity = Serializer.getInteger(bytes, i)
            i += Integer.BYTES
            type = Serializer.getInteger(bytes, i)
            i += Integer.BYTES
            arrivalTime = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            height = Serializer.getLong(bytes, i)
            i += java.lang.Long.BYTES
            /*
            confirmed = bytes[i] == 1;
            i++;
            */
            solid = bytes[i].toInt() == 1
            i++
            snapshot = Serializer.getInteger(bytes, i)
            i += Integer.BYTES
            val senderBytes = ByteArray(bytes.size - i)
            if (senderBytes.size != 0) {
                System.arraycopy(bytes, i, senderBytes, 0, senderBytes.size)
            }
            sender = String(senderBytes)
            parsed = true
        }
    }

    override fun merge(): Boolean {
        return false
    }

    companion object {
        val SIZE = 1604
    }
}
