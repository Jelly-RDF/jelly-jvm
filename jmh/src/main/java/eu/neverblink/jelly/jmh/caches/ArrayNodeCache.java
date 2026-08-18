package eu.neverblink.jelly.jmh.caches;

import java.util.function.Function;

/**
 * Direct-mapped cache: a power-of-two table indexed by the mixed hash, where a colliding key
 * overwrites the slot.
 */
public final class ArrayNodeCache<K, V> {

    private final Object[] keys;
    private final Object[] values;
    private final int mask;

    public ArrayNodeCache(int maxSize) {
        int capacity = Integer.highestOneBit(Math.max(maxSize - 1, 1)) << 1;
        this.keys = new Object[capacity];
        this.values = new Object[capacity];
        this.mask = capacity - 1;
    }

    private static int mix(int x) {
        final int h = x * 0x9E3779B1;
        return h ^ (h >>> 16);
    }

    @SuppressWarnings("unchecked")
    public V getOrCompute(K key, Function<K, V> mappingFunction) {
        final int pos = mix(key.hashCode()) & mask;
        if (key.equals(keys[pos])) {
            return (V) values[pos];
        }
        final var value = mappingFunction.apply(key);
        keys[pos] = key;
        values[pos] = value;
        return value;
    }
}
