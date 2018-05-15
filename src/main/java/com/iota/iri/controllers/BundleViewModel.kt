package com.iota.iri.controllers

import com.iota.iri.model.Bundle
import com.iota.iri.model.Hash
import com.iota.iri.storage.Indexable
import com.iota.iri.storage.Tangle

/**
 * Created by paul on 5/15/17.
 */

class BundleViewModel : HashesViewModel {
    private lateinit var self: Bundle
    private var hash: Indexable? = null

    constructor(hash: Hash) {
        this.hash = hash
    }

    private constructor(hashes: Bundle?, hash: Indexable) {
        this.self = hashes?.let { hashes } ?: Bundle()
        this.hash = hash
    }

    @Throws(Exception::class)
    override fun store(tangle: Tangle): Boolean {
        return tangle.save(self, hash)
    }

    override fun size(): Int {
        return self.set.size
    }

    override fun addHash(theHash: Hash): Boolean {
        return getHashes().add(theHash)
    }

    override fun getIndex(): Indexable? {
        return hash
    }

    override fun getHashes(): MutableSet<Hash> {
        return self.set
    }

    @Throws(Exception::class)
    override fun delete(tangle: Tangle) {
        tangle.delete(Bundle::class.java, hash)
    }

    @Throws(Exception::class)
    override fun next(tangle: Tangle): BundleViewModel? {
        val bundlePair = tangle.next(Bundle::class.java, hash)
        return bundlePair?.high?.let { BundleViewModel(bundlePair.high as Bundle, bundlePair.low as Hash) }
    }

    companion object {

        @Throws(Exception::class)
        fun load(tangle: Tangle, hash: Indexable): BundleViewModel {
            return BundleViewModel(tangle.load(Bundle::class.java, hash) as Bundle, hash)
        }

        @Throws(Exception::class)
        fun first(tangle: Tangle): BundleViewModel? {
            val bundlePair = tangle.getFirst(Bundle::class.java, Hash::class.java)
            return bundlePair?.high?.let { BundleViewModel(bundlePair.high as Bundle, bundlePair.low as Hash) }
        }
    }
}
