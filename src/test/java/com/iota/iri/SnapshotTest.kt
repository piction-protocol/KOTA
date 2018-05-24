package com.iota.iri

import com.iota.iri.conf.Configuration
import com.iota.iri.model.Hash
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.BeforeClass
import org.junit.Test
import java.io.IOException
import java.io.UncheckedIOException
import java.util.*

class SnapshotTest {

    private val modifiedMap: Map<Hash, Long>
        get() {
            val someHash = Hash("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS")
            val newMap: MutableMap<Hash, Long>
            newMap = HashMap()
            val iterator = newMap.entries.iterator()
            val entry: MutableMap.MutableEntry<Hash, Long>
            if (iterator.hasNext()) {
                entry = iterator.next()
                val value = entry.value
                val hash = entry.key
                newMap.put(hash, 0L)
                newMap.put(someHash, value)
            }
            return newMap
        }

    @Test
    fun getState() {
        //Assert.assertTrue(latestSnapshot.getState().equals(Snapshot.initialState));
    }

    @Test
    fun isConsistent() {
        Assert.assertTrue("Initial confirmed should be consistent", Snapshot.isConsistent(initSnapshot!!.state))
    }

    @Test
    fun patch() {
        val firstOne = initSnapshot!!.state.entries.iterator().next()
        val someHash = Hash("PSRQPWWIECDGDDZXHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS")
        val diff = HashMap<Hash, Long>()
        diff.put(firstOne.key, -firstOne.value)
        diff.put(someHash, firstOne.value)
        Assert.assertNotEquals(0, diff.size.toLong())
        Assert.assertTrue("The ledger should be consistent", Snapshot.isConsistent(initSnapshot!!.patchedDiff(diff)))
    }

    @Test
    fun applyShouldFail() {
        val latestSnapshot = initSnapshot!!.clone()
        val badMap = HashMap<Hash, Long>()
        badMap.put(Hash("PSRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), 100L)
        badMap.put(Hash("ESRQPWWIECDGDDZEHGJNMEVJNSVOSMECPPVRPEVRZFVIZYNNXZNTOTJOZNGCZNQVSPXBXTYUJUOXYASLS"), -100L)
        val patch = latestSnapshot.patchedDiff(badMap)
        assertFalse("should be inconsistent", Snapshot.isConsistent(latestSnapshot.patchedDiff(badMap)))
    }

    companion object {

        private var initSnapshot: Snapshot? = null

        @BeforeClass
        fun beforeClass() {
            try {
                initSnapshot = Snapshot.init(Configuration.MAINNET_SNAPSHOT_FILE,
                        Configuration.MAINNET_SNAPSHOT_SIG_FILE, false)
            } catch (e: IOException) {
                throw UncheckedIOException("Problem initiating snapshot", e)
            }
        }
    }
}