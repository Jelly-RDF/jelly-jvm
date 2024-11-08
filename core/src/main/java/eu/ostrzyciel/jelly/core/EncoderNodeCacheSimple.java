package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.UniversalTerm;

import java.util.function.Function;

class EncoderNodeCacheSimple extends EncoderNodeCache<UniversalTerm> {
    private final UniversalTerm[] values;

    EncoderNodeCacheSimple(int minimumSize) {
        super(minimumSize);
        UniversalTerm[] x = new UniversalTerm[sizeMinusOne + 1];
        values = x;
    }

    UniversalTerm getOrComputeIfAbsent(Object key, Function<Object, UniversalTerm> f) {
        final int idx = calcIndex(key);
        final Object storedKey = keys[idx];
        if (storedKey != null && (storedKey == key || storedKey.equals(key))) {
            return values[idx];
        } else {
            keys[idx] = key;
            UniversalTerm newTerm = f.apply(key);
            values[idx] = newTerm;
            return newTerm;
        }
    }
}
