package com.iota.iri.controllers

import com.iota.iri.model.Hash
import com.iota.iri.model.IntegerIndex
import com.iota.iri.model.Milestone
import com.iota.iri.storage.Tangle
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by paul on 4/11/17.
 */

class MilestoneViewModel {
    private val milestone: Milestone

    val hash: Hash
        get() = milestone.hash

    private constructor(milestone: Milestone) {
        this.milestone = milestone
    }

    constructor(index: Int, milestoneHash: Hash) {
        this.milestone = Milestone()
        this.milestone.index = IntegerIndex(index)
        this.milestone.hash = milestoneHash
    }

    @Throws(Exception::class)
    fun previous(tangle: Tangle): MilestoneViewModel? {
        val milestonePair = tangle.previous(Milestone::class.java, this.milestone.index)
        return milestonePair?.high?.let {
            val milestone = milestonePair.high as Milestone
            return MilestoneViewModel(milestone)
        }
    }

    @Throws(Exception::class)
    fun next(tangle: Tangle): MilestoneViewModel? {
        val milestonePair = tangle.next(Milestone::class.java, this.milestone.index)
        milestonePair?.high?.let {
            val milestone = milestonePair.high as Milestone
            return MilestoneViewModel(milestone)
        } ?: return null
    }

    @Throws(Exception::class)
    fun store(tangle: Tangle): Boolean {
        return tangle.save(milestone, milestone.index)!!
    }

    fun index(): Int? {
        return milestone.index.value
    }

    @Throws(Exception::class)
    fun delete(tangle: Tangle) {
        tangle.delete(Milestone::class.java, milestone.index)
    }

    companion object {
        private val milestones = ConcurrentHashMap<Int, MilestoneViewModel>()

        fun clear() {
            milestones.clear()
        }

        @Throws(Exception::class)
        operator fun get(tangle: Tangle, index: Int): MilestoneViewModel? {
            var milestoneViewModel: MilestoneViewModel? = milestones[index]
            if (milestoneViewModel == null && load(tangle, index)) {
                milestoneViewModel = milestones[index]
            }
            return milestoneViewModel
        }

        @Throws(Exception::class)
        fun load(tangle: Tangle, index: Int): Boolean {
            val milestone = tangle.load(Milestone::class.java, IntegerIndex(index)) as Milestone
            milestones.put(index, MilestoneViewModel(milestone))
            return true
        }

        @Throws(Exception::class)
        fun first(tangle: Tangle): MilestoneViewModel? {
            val milestonePair = tangle.getFirst(Milestone::class.java, IntegerIndex::class.java)
            milestonePair?.high?.let {
                val milestone = milestonePair.high as Milestone
                return MilestoneViewModel(milestone)
            } ?: return null
        }

        @Throws(Exception::class)
        fun latest(tangle: Tangle): MilestoneViewModel? {
            val milestonePair = tangle.getLatest(Milestone::class.java, IntegerIndex::class.java)
            milestonePair?.high?.let {
                val milestone = milestonePair.high as Milestone
                return MilestoneViewModel(milestone)
            } ?: return null
        }

        @Throws(Exception::class)
        fun findClosestPrevMilestone(tangle: Tangle, index: Int): MilestoneViewModel? {
            val milestonePair = tangle.previous(Milestone::class.java, IntegerIndex(index))
            return milestonePair?.high?.let {
                MilestoneViewModel(milestonePair.high as Milestone)
            }
        }

        @Throws(Exception::class)
        fun findClosestNextMilestone(tangle: Tangle, index: Int, testnet: Boolean,
                                     milestoneStartIndex: Int): MilestoneViewModel? {
            if (!testnet && index <= milestoneStartIndex) {
                return first(tangle)
            }

            val milestonePair = tangle.next(Milestone::class.java, IntegerIndex(index))
            return milestonePair?.high?.let {
                MilestoneViewModel(milestonePair.high as Milestone)
            }
        }
    }
}
