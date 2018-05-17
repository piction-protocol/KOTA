package com.iota.iri.hash


import java.util.*

/**
 * (c) 2016 Come-from-Beyond
 */
object ISSInPlace {

    val NUMBER_OF_FRAGMENT_CHUNKS = 27
    val FRAGMENT_LENGTH = Sponge.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS
    val TRYTE_WIDTH = 3
    private val NUMBER_OF_SECURITY_LEVELS = 3
    val NORMALIZED_FRAGMENT_LENGTH = Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS
    private val MIN_TRIT_VALUE = -1
    private val MAX_TRIT_VALUE = 1
    private val MIN_TRYTE_VALUE = -13
    private val MAX_TRYTE_VALUE = 13

    fun subseed(mode: SpongeFactory.Mode, subseed: IntArray, index: Int) {
        var index = index

        if (index < 0) {
            throw RuntimeException("Invalid subseed index: " + index)
        }

        if (subseed.size != Sponge.HASH_LENGTH) {
            throw IllegalArgumentException("Subseed array is not of HASH_LENGTH")
        }

        while (index-- > 0) {

            for (i in subseed.indices) {

                if (++subseed[i] > MAX_TRIT_VALUE) {
                    subseed[i] = MIN_TRIT_VALUE
                } else {
                    break
                }
            }
        }

        val hash = SpongeFactory.create(mode)
        hash!!.absorb(subseed, 0, subseed.size)
        hash.squeeze(subseed, 0, subseed.size)
    }

    fun key(mode: SpongeFactory.Mode, subseed: IntArray, key: IntArray) {

        if (subseed.size != Sponge.HASH_LENGTH) {
            throw RuntimeException("Invalid subseed length: " + subseed.size)
        }

        if (key.size % FRAGMENT_LENGTH != 0) {
            throw IllegalArgumentException("key length must be multiple of fragment length")
        }

        val numberOfFragments = key.size / FRAGMENT_LENGTH

        if (numberOfFragments <= 0) {
            throw RuntimeException("Invalid number of key fragments: " + numberOfFragments)
        }

        val hash = SpongeFactory.create(mode)
        hash!!.absorb(subseed, 0, subseed.size)
        hash.squeeze(key, 0, key.size)
    }

    fun digests(mode: SpongeFactory.Mode, key: IntArray, digests: IntArray) {

        if (key.size == 0 || key.size % FRAGMENT_LENGTH != 0) {
            throw RuntimeException("Invalid key length: " + key.size)
        }

        if (digests.size != key.size / FRAGMENT_LENGTH * Sponge.HASH_LENGTH) {
            throw IllegalArgumentException("Invalid digests length")
        }

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
    }

    fun address(mode: SpongeFactory.Mode, digests: IntArray, address: IntArray) {

        if (digests.size == 0 || digests.size % Sponge.HASH_LENGTH != 0) {
            throw RuntimeException("Invalid digests length: " + digests.size)
        }

        if (address.size != Sponge.HASH_LENGTH) {
            throw IllegalArgumentException("Invalid address length")
        }

        val hash = SpongeFactory.create(mode)
        hash!!.absorb(digests, 0, digests.size)
        hash.squeeze(address, 0, address.size)
    }


    fun digest(mode: SpongeFactory.Mode, normalizedBundleFragment: IntArray, nbOff: Int, signatureFragment: IntArray, sfOff: Int, digest: IntArray) {

        if (normalizedBundleFragment.size - nbOff < Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) {
            throw RuntimeException("Invalid normalized bundleValidator fragment length: " + normalizedBundleFragment.size)
        }
        if (signatureFragment.size - sfOff < FRAGMENT_LENGTH) {
            throw RuntimeException("Invalid signature fragment length: " + signatureFragment.size)
        }

        if (digest.size != Sponge.HASH_LENGTH) {
            throw IllegalArgumentException("Invalid digest array length.")
        }

        val buffer = Arrays.copyOfRange(signatureFragment, sfOff, sfOff + FRAGMENT_LENGTH)
        val hash = SpongeFactory.create(mode)

        for (j in 0 until NUMBER_OF_FRAGMENT_CHUNKS) {

            var k = normalizedBundleFragment[nbOff + j] - MIN_TRYTE_VALUE
            while (k-- > 0) {
                hash!!.reset()
                hash.absorb(buffer, j * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
                hash.squeeze(buffer, j * Sponge.HASH_LENGTH, Sponge.HASH_LENGTH)
            }
        }
        hash!!.reset()
        hash.absorb(buffer, 0, buffer.size)
        hash.squeeze(digest, 0, digest.size)
    }


    fun normalizedBundle(bundle: IntArray, normalizedBundle: IntArray) {
        if (bundle.size != Sponge.HASH_LENGTH) {
            throw RuntimeException("Invalid bundleValidator length: " + bundle.size)
        }

        for (i in 0 until NUMBER_OF_SECURITY_LEVELS) {
            var sum = 0
            for (j in i * (Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) until (i + 1) * (Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS)) {

                normalizedBundle[j] = bundle[j * TRYTE_WIDTH] + bundle[j * TRYTE_WIDTH + 1] * 3 + bundle[j * TRYTE_WIDTH + 2] * 9
                sum += normalizedBundle[j]
            }
            if (sum > 0) {
                while (sum-- > 0) {

                    for (j in i * (Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) until (i + 1) * (Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS)) {

                        if (normalizedBundle[j] > MIN_TRYTE_VALUE) {
                            normalizedBundle[j]--
                            break
                        }
                    }
                }
            } else {
                while (sum++ < 0) {
                    for (j in i * (Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS) until (i + 1) * (Sponge.HASH_LENGTH / TRYTE_WIDTH / NUMBER_OF_SECURITY_LEVELS)) {

                        if (normalizedBundle[j] < MAX_TRYTE_VALUE) {
                            normalizedBundle[j]++
                            break
                        }
                    }
                }
            }
        }
    }
}
