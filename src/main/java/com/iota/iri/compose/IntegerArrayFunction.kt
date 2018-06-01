package com.iota.iri.compose

import com.iota.iri.utils.Converter
import java.util.function.Function

class IntegerArrayFunction : Function<IntArray, String> {
    override fun apply(t: IntArray): String {
        return Converter.trytes(t)
    }
}
