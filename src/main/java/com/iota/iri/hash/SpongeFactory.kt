package com.iota.iri.hash

/**
 * Created by paul on 7/27/17.
 */

object SpongeFactory {
    enum class Mode {
        CURLP81,
        CURLP27,
        KERL
    }

    fun create(mode: Mode): Sponge? {
        when (mode) {
            SpongeFactory.Mode.CURLP81 -> return Curl(mode)
            SpongeFactory.Mode.CURLP27 -> return Curl(mode)
            SpongeFactory.Mode.KERL -> return Kerl()
            else -> return null
        }
    }
}
