package com.iota.iri.controllers

import com.iota.iri.conf.Configuration
import com.iota.iri.model.Hash
import com.iota.iri.storage.Tangle
import com.iota.iri.storage.rocksDB.RocksDBPersistenceProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Created by paul on 4/11/17.
 */
class MilestoneViewModelTest {
    internal val dbFolder = TemporaryFolder()
    internal val logFolder = TemporaryFolder()
    internal var index = 30

    @Before
    @Throws(Exception::class)
    fun setUpTest() {
        dbFolder.create()
        logFolder.create()
        val rocksDBPersistenceProvider: RocksDBPersistenceProvider
        rocksDBPersistenceProvider = RocksDBPersistenceProvider(dbFolder.root.absolutePath,
                logFolder.root.absolutePath, 1000)
        tangle.addPersistenceProvider(rocksDBPersistenceProvider)
        tangle.init()
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        tangle.shutdown()
        dbFolder.delete()
        logFolder.delete()
    }

    @Test
    @Throws(Exception::class)
    fun getMilestone() {
        val milestoneHash = Hash("ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val milestoneViewModel = MilestoneViewModel(++index, milestoneHash)
        assertTrue(milestoneViewModel.store(tangle))
        MilestoneViewModel.clear()
        MilestoneViewModel.load(tangle, index)
        assertEquals(MilestoneViewModel[tangle, index]!!.hash, milestoneHash)
    }

    @Test
    @Throws(Exception::class)
    fun store() {
        val milestoneViewModel = MilestoneViewModel(++index, Hash.NULL_HASH)
        assertTrue(milestoneViewModel.store(tangle))
    }

    @Test
    @Throws(Exception::class)
    fun snapshot() {
        val milestoneHash = Hash("BBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val value: Long = 3
        val milestoneViewModel = MilestoneViewModel(++index, milestoneHash)
    }

    @Test
    @Throws(Exception::class)
    fun initSnapshot() {
        val milestoneViewModel = MilestoneViewModel(++index, Hash.NULL_HASH)
    }

    @Test
    @Throws(Exception::class)
    fun updateSnapshot() {
        val milestoneHash = Hash("CBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val milestoneViewModel = MilestoneViewModel(++index, milestoneHash)
        assertTrue(milestoneViewModel.store(tangle))
        MilestoneViewModel.clear()
        assertEquals(MilestoneViewModel[tangle, index]!!.hash, milestoneHash)

    }

    @Test
    @Throws(Exception::class)
    fun getHash() {
        val milestoneHash = Hash("DBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val milestoneViewModel = MilestoneViewModel(++index, milestoneHash)
        assertEquals(milestoneHash, milestoneViewModel.hash)
    }

    @Test
    @Throws(Exception::class)
    fun index() {
        val milestoneHash = Hash("EBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val milestoneViewModel = MilestoneViewModel(++index, milestoneHash)
        assertTrue(index == milestoneViewModel.index())
    }

    @Test
    @Throws(Exception::class)
    fun latest() {
        val top = 100
        val milestoneHash = Hash("ZBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val milestoneViewModel = MilestoneViewModel(top, milestoneHash)
        milestoneViewModel.store(tangle)
        assertTrue(top == MilestoneViewModel.latest(tangle)?.index())
    }

    @Test
    @Throws(Exception::class)
    fun first() {
        val first = 1
        val milestoneHash = Hash("99CDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")
        val milestoneViewModel = MilestoneViewModel(first, milestoneHash)
        milestoneViewModel.store(tangle)
        assertTrue(first == MilestoneViewModel.first(tangle)?.index())
    }

    @Test
    @Throws(Exception::class)
    operator fun next() {
        val first = 1
        val next = 2
        val firstMilestone = MilestoneViewModel(first, Hash("99CDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        firstMilestone.store(tangle)
        MilestoneViewModel(next, Hash("9ACDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)

        assertTrue(next == MilestoneViewModel.first(tangle)?.next(tangle)?.index())
    }

    @Test
    @Throws(Exception::class)
    fun previous() {
        val first = 1
        val next = 2
        val nextMilestone = MilestoneViewModel(next, Hash("99CDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        nextMilestone.store(tangle)
        MilestoneViewModel(first, Hash("9ACDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)

        assertTrue(first == nextMilestone.previous(tangle)?.index())
    }

    @Test
    @Throws(Exception::class)
    fun latestSnapshot() {
        val nosnapshot = 90
        val topSnapshot = 80
        val mid = 50
        MilestoneViewModel(nosnapshot, Hash("FBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)
        val milestoneViewModelmid = MilestoneViewModel(mid, Hash("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        milestoneViewModelmid.store(tangle)
        val milestoneViewModeltopSnapshot = MilestoneViewModel(topSnapshot, Hash("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        milestoneViewModeltopSnapshot.store(tangle)
        //assertTrue(topSnapshot == MilestoneViewModel.latestWithSnapshot().index());
    }

    @Test
    @Throws(Exception::class)
    fun firstWithSnapshot() {
        val first = 5
        val firstSnapshot = 6
        val next = 7
        MilestoneViewModel(first, Hash("FBCDEFGHIJ9LMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)
        val milestoneViewModelmid = MilestoneViewModel(next, Hash("GBCDE9GHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        milestoneViewModelmid.store(tangle)
        val milestoneViewModeltopSnapshot = MilestoneViewModel(firstSnapshot, Hash("GBCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYA9ABCDEFGHIJKLMNOPQRSTUV99999"))
        milestoneViewModeltopSnapshot.store(tangle)
        //assertTrue(firstSnapshot == MilestoneViewModel.firstWithSnapshot().index());
    }

    @Test
    @Throws(Exception::class)
    fun nextWithSnapshot() {
        val firstSnapshot = 8
        val next = 9
        val milestoneViewModelmid = MilestoneViewModel(next, Hash("GBCDEBGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        milestoneViewModelmid.store(tangle)
        val milestoneViewModel = MilestoneViewModel(firstSnapshot, Hash("GBCDEFGHIJKLMNODQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999"))
        milestoneViewModel.store(tangle)
        //assertTrue(next == milestoneViewModel.nextWithSnapshot().index());
    }

    @Test
    @Throws(Exception::class)
    fun nextGreaterThan() {
        val milestoneStartIndex = Integer.parseInt(Configuration.MAINNET_MILESTONE_START_INDEX)
        val first = milestoneStartIndex + 1
        val next = first + 1
        MilestoneViewModel(next, Hash("GBCDEBGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)
        MilestoneViewModel(first, Hash("GBCDEFGHIJKLMNODQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)
        assertEquals(next.toLong(), MilestoneViewModel.findClosestNextMilestone(
                tangle, first, false, milestoneStartIndex)!!.index()!!.toInt().toLong())
    }

    @Test
    @Throws(Exception::class)
    fun PrevBefore() {
        val first = 8
        val next = 9
        MilestoneViewModel(next, Hash("GBCDEBGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)
        MilestoneViewModel(first, Hash("GBCDEFGHIJKLMNODQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUVWXYZ9ABCDEFGHIJKLMNOPQRSTUV99999")).store(tangle)
        assertEquals(first.toLong(), MilestoneViewModel.findClosestPrevMilestone(tangle, next)!!.index()!!.toInt().toLong())
    }

    companion object {
        private val tangle = Tangle()
    }
}