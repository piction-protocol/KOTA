package com.iota.iri.compose

import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.function.Function

class URIFunction: Function<String, Optional<URI>> {
    override fun apply(t: String): Optional<URI> {
        try {
            return Optional.of(URI(t))
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        return Optional.empty()
    }
}
