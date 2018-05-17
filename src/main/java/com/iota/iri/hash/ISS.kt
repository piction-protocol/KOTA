package com.iota.iri.hash

import com.iota.iri.model.Hash
import java.util.*

/**
 * (c) 2016 Come-from-Beyond
 */
object ISS {

    val NUMBER_OF_FRAGMENT_CHUNKS = 27
    val FRAGMENT_LENGTH = Sponge.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS
    private val NUMBER_OF_SECURITY_LEVELS = 3

    private val MIN_TRIT_VALUE = -1
    private val MAX_TRIT_VALUE = 1
    val TRYTE_WIDTH = 3
    private val MIN_TRYTE_VALUE = -13
    private val MAX_TRYTE_VALUE = 13
    val NORMALIZED_FRAGMENT_LENGTH = Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS

    fun subseed(mode: SpongeFactory.Mode, seed: IntArray, index: Int): IntArray {
        var index = index

        if (index < 0) {
            throw RuntimeException("Invalid subseed index: " + index)
        }

        val subseedPreimage = Arrays.copyOf(seed, seed.size)

        while (index-- > 0) {

            for (i in subseedPreimage.indices) {

                if (++subseedPreimage[i] > MAX_TRIT_VALUE) {
                    subseedPreimage[i] = MIN_TRIT_VALUE
                } else {
                    break
                }
            }
        }

        val subseed = IntArray(Sponge.HASH_LENGTH)

        val hash = SpongeFactory.create(mode)
        hash!!.absorb(subseedPreimage, 0, subseedPreimage.size)
        hash.squeeze(subseed, 0, subseed.size)
        return subseed
    }

    fun key(mode: SpongeFactory.Mode, subseed: IntArray, numberOfFragments: Int): IntArray {

        if (subseed.size != Sponge.HASH_LENGTH) {
            throw RuntimeException("Invalid subseed length: " + subseed.size)
        }
        if (numberOfFragments <= 0) {
            throw RuntimeException("Invalid number of key fragments: " + numberOfFragments)
        }

        val key = IntArray(FRAGMENT_LENGTH * numberOfFragments)

        val hash = SpongeFactory.create(mode)
        hash!!.absorb(subseed, 0, subseed.size)
        hash.squeeze(key, 0, key.size)
        return key
    }

    fun digests(mode: SpongeFactory.Mode, key: IntArray): IntArray {

        if (key.size == 0 || key.size % FRAGMENT_LENGTH != 0) {
            throw RuntimeException("Invalid key length: " + key.size)
        }

        val digests = IntArray(key.size / FRAGMENT_LENGTH * Sponge.HASH_LENGTH)
        val hash = SpongeFactory.create(mode)

        for (i in 0 until key.size / FRAGMENT_LENGTH) {

            val buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH)
            for (j in 0 until NUMBER_OF_FRAGMENT_CHUNKS) {

                var k = MAX_TRYTE_VALUE - MIN_TRYTE_VALUE
                while (k-- > 0) {
                    hash!!.reset()
                    hash.absorb(buffer, j * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
                    hash.squeeze(buffer, j * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
                }
            }
            hash!!.reset()
            hash.absorb(buffer, 0, buffer.size)
            hash.squeeze(digests, i * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
        }

        return digests
    }

    fun address(mode: SpongeFactory.Mode, digests: IntArray): IntArray {

        if (digests.size == 0 || digests.size % Sponge.HASH_LENGTH != 0) {
            throw RuntimeException("Invalid digests length: " + digests.size)
        }

        val address = IntArray(Sponge.HASH_LENGTH)

        val hash = SpongeFactory.create(mode)
        hash!!.absorb(digests, 0, digests.size)
        hash.squeeze(address, 0, address.size)

        return address
    }

    fun normalizedBundle(bundle: IntArray): IntArray {

        if (bundle.size != Sponge.HASH_LENGTH) {
            throw RuntimeException("Invalid bundleValidator length: " + bundle.size)
        }

        val normalizedBundle = IntArray(Sponge.HASH_LENGTH / TRYTE_WIDTH)

        ISSInPlace.normalizedBundle(bundle, normalizedBundle)
        return normalizedBundle
    }

    fun signatureFragment(mode: SpongeFactory.Mode, normalizedBundleFragment: IntArray, keyFragment: IntArray): IntArray {

        if (normalizedBundleFragment.size != NORMALIZED_FRAGMENT_LENGTH) {
            throw RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.size)
        }
        if (keyFragment.size != FRAGMENT_LENGTH) {
            throw RuntimeException("Invalid key fragment length: " + keyFragment.size)
        }

        val signatureFragment = Arrays.copyOf(keyFragment, keyFragment.size)
        val hash = SpongeFactory.create(mode)

        for (j in 0 until NUMBER_OF_FRAGMENT_CHUNKS) {

            var k = MAX_TRYTE_VALUE - normalizedBundleFragment[j]
            while (k-- > 0) {
                hash!!.reset()
                hash.absorb(signatureFragment, j * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
                hash.squeeze(signatureFragment, j * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
            }
        }

        return signatureFragment
    }

    fun digest(mode: SpongeFactory.Mode, normalizedBundleFragment: IntArray, signatureFragment: IntArray): IntArray {

        if (normalizedBundleFragment.size != Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) {
            throw RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.size)
        }
        if (signatureFragment.size != FRAGMENT_LENGTH) {
            throw RuntimeException("Invalid signature fragment length: " + signatureFragment.size)
        }

        val digest = IntArray(Sponge.HASH_LENGTH)
        ISSInPlace.digest(mode, normalizedBundleFragment, 0, signatureFragment, 0, digest)
        return digest
    }

    fun getMerkleRoot(mode: SpongeFactory.Mode, hash: IntArray, trits: IntArray, offset: Int, indexIn: Int, size: Int): IntArray {
        var index = indexIn
        val curl = SpongeFactory.create(mode)
        for (i in 0 until size) {
            curl!!.reset()
            if (index and 1 == 0) {
                curl.absorb(hash, 0, hash.size)
                curl.absorb(trits, offset + i * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
            } else {
                curl.absorb(trits, offset + i * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
                curl.absorb(hash, 0, hash.size)
            }
            curl.squeeze(hash, 0, hash.size)

            index = index shr 1
        }
        return if (index != 0) {
            Hash.NULL_HASH.trits()
        } else hash
    }
}
