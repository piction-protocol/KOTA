package com.iota.iri.controllers

import com.iota.iri.model.Approvee
import com.iota.iri.model.Hash
import com.iota.iri.storage.Indexable
import com.iota.iri.storage.Tangle

/**
 * Created by paul on 5/15/17.
 */

class ApproveeViewModel : HashesViewModel {
    private lateinit var self: Approvee
    private var hash: Indexable? = null

    constructor(hash: Hash) {
        this.hash = hash
    }

    private constructor(hashes: Approvee?, hash: Indexable) {
        this.self = hashes?.let { hashes } ?: Approvee()
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
        return hashes.add(theHash)
    }

    override fun getIndex(): Indexable? {
        return hash
    }

    override fun getHashes(): MutableSet<Hash> {
        return self.set
    }

    @Throws(Exception::class)
    override fun delete(tangle: Tangle) {
        tangle.delete(Approvee::class.java, hash)
    }

    @Throws(Exception::class)
    override fun next(tangle: Tangle): ApproveeViewModel? {
        val bundlePair = tangle.next(Approvee::class.java, hash)
        return bundlePair?.high?.let { ApproveeViewModel(bundlePair.high as Approvee, bundlePair.low as Hash) }
    }

    companion object {

        @Throws(Exception::class)
        fun load(tangle: Tangle, hash: Indexable): ApproveeViewModel {
            return ApproveeViewModel(tangle.load(Approvee::class.java, hash) as Approvee, hash)
        }

        @Throws(Exception::class)
        fun first(tangle: Tangle): ApproveeViewModel? {
            val bundlePair = tangle.getFirst(Approvee::class.java, Hash::class.java)
            return bundlePair?.high?.let { ApproveeViewModel(bundlePair.high as Approvee, bundlePair.low as Hash) }
        }
    }
}
