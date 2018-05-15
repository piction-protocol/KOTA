package com.iota.iri.utils;

public class Pair<S, T> {
    public final S low;
    public final T high;

    public Pair(S k, T v) {
        low = k;
        high = v;
    }
}
