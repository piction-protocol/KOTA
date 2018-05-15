package com.iota.iri.controllers

import com.iota.iri.model.Hash
import java.security.SecureRandom
import java.util.*

class TipsViewModel {

    private val tips = FifoHashCache<Hash>(TipsViewModel.MAX_TIPS)
    private val solidTips = FifoHashCache<Hash>(TipsViewModel.MAX_TIPS)

    private val seed = SecureRandom()
    private val sync = Any()

    val randomSolidTipHash: Hash?
        get() = synchronized(sync) {
            val size = solidTips.size()
            if (size == 0) {
                return randomNonSolidTipHash
            }
            var index = seed.nextInt(size)
            val hashIterator: Iterator<Hash> = solidTips.iterator()
            var hash: Hash? = null
            while (index-- >= 0 && hashIterator.hasNext()) {
                hash = hashIterator.next()
            }
            return hash
        }

    val randomNonSolidTipHash: Hash?
        get() = synchronized(sync) {
            val size = tips.size()
            if (size == 0) {
                return null
            }
            var index = seed.nextInt(size)
            val hashIterator: Iterator<Hash> = tips.iterator()
            var hash: Hash? = null
            while (index-- >= 0 && hashIterator.hasNext()) {
                hash = hashIterator.next()
            }
            return hash
        }

    fun addTipHash(hash: Hash) {
        synchronized(sync) {
            tips.add(hash)
        }
    }

    fun removeTipHash(hash: Hash) {
        synchronized(sync) {
            if (!tips.remove(hash)) {
                solidTips.remove(hash)
            }
        }
    }

    fun setSolid(tip: Hash) {
        synchronized(sync) {
            if (tips.remove(tip)) {
                solidTips.add(tip)
            }
        }
    }

    fun getTips(): Set<Hash> {
        val hashes = HashSet<Hash>()
        synchronized(sync) {
            var hashIterator: Iterator<Hash>
            hashIterator = tips.iterator()
            while (hashIterator.hasNext()) {
                hashes.add(hashIterator.next())
            }

            hashIterator = solidTips.iterator()
            while (hashIterator.hasNext()) {
                hashes.add(hashIterator.next())
            }
        }
        return hashes
    }

    fun nonSolidSize(): Int {
        synchronized(sync) {
            return tips.size()
        }
    }

    fun size(): Int {
        synchronized(sync) {
            return tips.size() + solidTips.size()
        }
    }

    private inner class FifoHashCache<K>(private val capacity: Int) {

        private val set: LinkedHashSet<K> = LinkedHashSet()

        fun add(key: K): Boolean {
            val vacancy = this.capacity - this.set.size
            if (vacancy <= 0) {
                val it = this.set.iterator()
                for (i in vacancy..0) {
                    it.next()
                    it.remove()
                }
            }
            return this.set.add(key)
        }

        fun remove(key: K): Boolean {
            return this.set.remove(key)
        }

        fun size(): Int {
            return this.set.size
        }

        operator fun iterator(): Iterator<K> {
            return this.set.iterator()
        }
    }

    companion object {

        val MAX_TIPS = 5000
    }

}