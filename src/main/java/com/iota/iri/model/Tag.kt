package com.iota.iri.model

/**
 * Created by paul on 5/15/17.
 */

open class Tag : Hashes {

    constructor()

    constructor(hash: Hash) {
        set.add(hash)
    }
}
