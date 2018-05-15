package com.iota.iri.hash

import com.iota.iri.utils.Converter
import com.iota.iri.utils.Pair
import java.util.*

/**
 * (c) 2016 Come-from-Beyond and Paul Handy
 *
 * Curl belongs to the sponge function family.
 *
 */

class Curl : Sponge {
    private val numberOfRounds: Int
    private val state: IntArray?
    private val stateLow: LongArray?
    private val stateHigh: LongArray?

    private val scratchpad = IntArray(STATE_LENGTH)

    constructor(mode: SpongeFactory.Mode) {
        when (mode) {
            SpongeFactory.Mode.CURLP27 -> numberOfRounds = NUMBER_OF_ROUNDSP27
            SpongeFactory.Mode.CURLP81 -> numberOfRounds = NUMBER_OF_ROUNDSP81
            else -> throw NoSuchElementException("Only Curl-P-27 and Curl-P-81 are supported.")
        }
        state = IntArray(STATE_LENGTH)
        stateHigh = null
        stateLow = null
    }

    constructor(pair: Boolean, mode: SpongeFactory.Mode) {
        when (mode) {
            SpongeFactory.Mode.CURLP27 -> numberOfRounds = NUMBER_OF_ROUNDSP27
            SpongeFactory.Mode.CURLP81 -> numberOfRounds = NUMBER_OF_ROUNDSP81
            else -> throw NoSuchElementException("Only Curl-P-27 and Curl-P-81 are supported.")
        }

        when(pair) {
            true -> {
                stateHigh = LongArray(STATE_LENGTH)
                stateLow = LongArray(STATE_LENGTH)
                state = null
                set()
            }
            false -> {
                state = IntArray(STATE_LENGTH)
                stateHigh = null
                stateLow = null
            }
        }
    }

    private fun setMode(mode: SpongeFactory.Mode) {

    }

    override fun absorb(trits: IntArray, offset: Int, length: Int) {
        var offset = offset
        var length = length

        do {
            System.arraycopy(trits, offset, state!!, 0, if (length < Sponge.HASH_LENGTH) length else Sponge.HASH_LENGTH)
            transform()
            offset += Sponge.HASH_LENGTH
            length -= Sponge.HASH_LENGTH
        } while (length > 0)
    }


    override fun squeeze(trits: IntArray, offset: Int, length: Int) {
        var offset = offset
        var length = length

        do {
            System.arraycopy(state!!, 0, trits, offset, if (length < Sponge.HASH_LENGTH) length else Sponge.HASH_LENGTH)
            transform()
            offset += Sponge.HASH_LENGTH
            length -= Sponge.HASH_LENGTH
        } while (length > 0)
    }

    private fun transform() {

        var scratchpadIndex = 0
        var prev_scratchpadIndex = 0
        for (round in 0 until numberOfRounds) {
            System.arraycopy(state!!, 0, scratchpad, 0, STATE_LENGTH)
            for (stateIndex in 0 until STATE_LENGTH) {
                prev_scratchpadIndex = scratchpadIndex
                if (scratchpadIndex < 365) {
                    scratchpadIndex += 364
                } else {
                    scratchpadIndex += -365
                }
                state[stateIndex] = TRUTH_TABLE[scratchpad[prev_scratchpadIndex] + (scratchpad[scratchpadIndex] shl 2) + 5]
            }
        }
    }

    override fun reset() {
        Arrays.fill(state!!, 0)
    }

    fun reset(pair: Boolean) {
        if (pair) {
            set()
        } else {
            reset()
        }
    }

    private fun set() {
        Arrays.fill(stateLow!!, Converter.HIGH_LONG_BITS)
        Arrays.fill(stateHigh!!, Converter.HIGH_LONG_BITS)
    }

    private fun pairTransform() {
        val curlScratchpadLow = LongArray(STATE_LENGTH)
        val curlScratchpadHigh = LongArray(STATE_LENGTH)
        var curlScratchpadIndex = 0
        var round = numberOfRounds
        while (round-- > 0) {
            System.arraycopy(stateLow!!, 0, curlScratchpadLow, 0, STATE_LENGTH)
            System.arraycopy(stateHigh!!, 0, curlScratchpadHigh, 0, STATE_LENGTH)
            for (curlStateIndex in 0 until STATE_LENGTH) {
                val alpha = curlScratchpadLow[curlScratchpadIndex]
                val beta = curlScratchpadHigh[curlScratchpadIndex]
                curlScratchpadIndex += (if (curlScratchpadIndex < 365) 364 else -365)
                val gamma = curlScratchpadHigh[curlScratchpadIndex]
                val delta = alpha or gamma.inv() and (curlScratchpadLow[curlScratchpadIndex] xor beta)
                stateLow[curlStateIndex] = delta.inv()
                stateHigh[curlStateIndex] = alpha xor gamma or delta
            }
        }
    }

    fun absorb(pair: Pair<LongArray, LongArray>, offset: Int, length: Int) {
        var o = offset
        var len = length
        do {
            System.arraycopy(pair.low, o, stateLow!!, 0, if (len < Sponge.HASH_LENGTH) len else Sponge.HASH_LENGTH)
            System.arraycopy(pair.high, o, stateHigh!!, 0, if (len < Sponge.HASH_LENGTH) len else Sponge.HASH_LENGTH)
            pairTransform()
            o += Sponge.HASH_LENGTH
            len -= Sponge.HASH_LENGTH
        } while (len > 0)
    }

    fun squeeze(pair: Pair<LongArray, LongArray>, offset: Int, length: Int): Pair<LongArray, LongArray> {
        var o = offset
        var len = length
        val low = pair.low
        val high = pair.high
        do {
            System.arraycopy(stateLow!!, 0, low, o, if (len < Sponge.HASH_LENGTH) len else Sponge.HASH_LENGTH)
            System.arraycopy(stateHigh!!, 0, high, o, if (len < Sponge.HASH_LENGTH) len else Sponge.HASH_LENGTH)
            pairTransform()
            o += Sponge.HASH_LENGTH
            len -= Sponge.HASH_LENGTH
        } while (len > 0)
        return Pair(low, high)
    }

    companion object {

        val NUMBER_OF_ROUNDSP81 = 81
        val NUMBER_OF_ROUNDSP27 = 27
        private val STATE_LENGTH = 3 * Sponge.HASH_LENGTH
        private val HALF_LENGTH = 364

        private val TRUTH_TABLE = intArrayOf(1, 0, -1, 2, 1, -1, 0, 2, -1, 1, 0)
    }

}
