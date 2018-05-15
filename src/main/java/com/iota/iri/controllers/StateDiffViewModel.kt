package com.iota.iri.controllers

import com.iota.iri.model.Hash
import com.iota.iri.model.StateDiff
import com.iota.iri.storage.Tangle

/**
 * Created by paul on 5/6/17.
 */

class StateDiffViewModel {
    private var stateDiff: StateDiff

    var hash: Hash
        private set

    val isEmpty: Boolean
        get() = stateDiff.state.isEmpty()

    val diff: Map<Hash, Long>
        get() = stateDiff.state

    constructor(state: Map<Hash, Long>, hash: Hash) {
        this.hash = hash
        this.stateDiff = StateDiff()
        this.stateDiff.state = state.toMutableMap()
    }

    constructor(diff: StateDiff?, hash: Hash) {
        this.hash = hash
        this.stateDiff = diff?.state?.let { diff } ?: StateDiff()
    }

    @Throws(Exception::class)
    fun store(tangle: Tangle): Boolean {
        return tangle.save(stateDiff, hash)!!
    }

    @Throws(Exception::class)
    fun delete(tangle: Tangle) {
        tangle.delete(StateDiff::class.java, hash)
    }

    companion object {

        @Throws(Exception::class)
        fun load(tangle: Tangle, hash: Hash): StateDiffViewModel {
            return StateDiffViewModel(tangle.load(StateDiff::class.java, hash) as StateDiff, hash)
        }

        @Throws(Exception::class)
        fun maybeExists(tangle: Tangle, hash: Hash): Boolean {
            return tangle.maybeHas(StateDiff::class.java, hash)!!
        }
    }
}