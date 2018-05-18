package com.iota.iri.controllers

import com.iota.iri.model.Hash
import com.iota.iri.model.ObsoleteTag
import com.iota.iri.model.Tag
import com.iota.iri.storage.Indexable
import com.iota.iri.storage.Tangle

/**
 * Created by paul on 5/15/17.
 */

class TagViewModel : HashesViewModel {
    private lateinit var self: Tag
    private var hash: Indexable? = null

    constructor(hash: Hash) {
        this.hash = hash
    }

    private constructor(hashes: Tag?, hash: Indexable) {
        this.self = hashes?.let { hashes } ?: Tag()
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
        tangle.delete(Tag::class.java, hash)
    }

    @Throws(Exception::class)
    override fun next(tangle: Tangle): TagViewModel? {
        val bundlePair = tangle.next(Tag::class.java, hash)
        return if (bundlePair != null) {
            TagViewModel(bundlePair.high as Tag, bundlePair.low as Hash)
        } else null
    }

    companion object {

        @Throws(Exception::class)
        private fun load(tangle: Tangle, hash: Indexable, model: Class<out Tag>): TagViewModel {
            return TagViewModel(tangle.load(model, hash) as Tag, hash)
        }

        @Throws(Exception::class)
        fun load(tangle: Tangle, hash: Indexable): TagViewModel {
            return load(tangle, hash, Tag::class.java)
        }

        @Throws(Exception::class)
        fun loadObsolete(tangle: Tangle, hash: Indexable): TagViewModel {
            return load(tangle, hash, ObsoleteTag::class.java)
        }

        @Throws(Exception::class)
        fun first(tangle: Tangle): TagViewModel? {
            val bundlePair = tangle.getFirst(Tag::class.java, Hash::class.java)
            return if (bundlePair != null) {
                TagViewModel(bundlePair.high as Tag, bundlePair.low as Hash)
            } else null
        }
    }
}
