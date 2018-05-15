package com.iota.iri.controllers

import com.iota.iri.model.Hash
import com.iota.iri.storage.Indexable
import com.iota.iri.storage.Tangle

/**
 * Created by paul on 5/6/17.
 */
interface HashesViewModel {
    fun size(): Int
    fun addHash(theHash: Hash): Boolean

    fun getIndex(): Indexable?
    fun getHashes(): Set<Hash>

    @Throws(Exception::class)
    fun store(tangle: Tangle): Boolean

    @Throws(Exception::class)
    fun next(tangle: Tangle): HashesViewModel?

    @Throws(Exception::class)
    fun delete(tangle: Tangle)
}
