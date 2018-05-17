package com.iota.iri.compose

import java.net.URI
import java.util.*
import java.util.function.Function

class OURIFunction: Function<Optional<URI>, URI> {
    override fun apply(t: Optional<URI>): URI {
        return t.get()
    }
}
