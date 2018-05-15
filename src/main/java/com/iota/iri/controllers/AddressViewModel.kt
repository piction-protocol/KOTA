package com.iota.iri.controllers

import com.iota.iri.model.Address
import com.iota.iri.model.Bundle
import com.iota.iri.model.Hash
import com.iota.iri.storage.Indexable
import com.iota.iri.storage.Persistable
import com.iota.iri.storage.Tangle
import com.iota.iri.utils.Pair

import java.util.HashMap

/**
 * Created by paul on 5/15/17.
 */

class AddressViewModel : HashesViewModel {
    private lateinit var self: Address
    private var hash: Indexable? = null

    constructor(hash: Hash) {
        this.hash = hash
    }

    private constructor(hashes: Address?, hash: Indexable) {
        this.self = hashes?.let { hashes } ?: Address()
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
        tangle.delete(Address::class.java, hash)
    }

    @Throws(Exception::class)
    override fun next(tangle: Tangle): AddressViewModel? {
        val bundlePair = tangle.next(Address::class.java, hash)
        return bundlePair?.hi?.let {
            AddressViewModel(bundlePair.hi as Address, bundlePair.low as Hash)
        }
    }

    companion object {

        @Throws(Exception::class)
        fun load(tangle: Tangle, hash: Indexable): AddressViewModel {
            return AddressViewModel(tangle.load(Address::class.java, hash) as Address, hash)
        }

        @Throws(Exception::class)
        fun first(tangle: Tangle): AddressViewModel? {
            val bundlePair = tangle.getFirst(Address::class.java, Hash::class.java)
            return bundlePair?.hi?.let {
                AddressViewModel(bundlePair.hi as Address, bundlePair.low as Hash)
            }
        }
    }
}
