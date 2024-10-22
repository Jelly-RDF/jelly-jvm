package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.*;
import java.util.function.Supplier;

public class OtherNodeCache extends EncoderNodeCache<Object, UniversalTerm> {
    private final UniversalTerm[] values;

    public OtherNodeCache(int minimumSize) {
        super(minimumSize);

        UniversalTerm[] x = new UniversalTerm[sizeMinusOne + 1];
        values = x;
    }

    public UniversalTerm computeIfAbsent(Object key, Supplier<UniversalTerm> supplier) {
        final int idx = calcIndex(key);
        if (keys[idx] != null && keys[idx].equals(key)) {
            return values[idx];
        } else {
            final var value = supplier.get();
            values[idx] = value;
            keys[idx] = key;
            return value;
        }
    }
}
