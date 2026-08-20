package eu.neverblink.jelly.jmh.caches;

import java.util.function.Function;

/**
 * Direct-mapped cache: a power-of-two table indexed by the mixed hash, where a colliding key
 * overwrites the slot.
 *
 * <p>The same table can also be read 2-way set associative, see {@link #getOrCompute2Way}. Both
 * geometries use the same capacity, so a benchmark comparing them compares hit rate and probe cost,
 * not memory.
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

    /**
     * Reads the table as 2-way set associative: the hash picks a pair of adjacent slots and either
     * of them can hold the key. The pair is kept most-recent-first, so a miss evicts the older entry.
     */
    @SuppressWarnings("unchecked")
    public V getOrCompute2Way(K key, Function<K, V> mappingFunction) {
        // Half as many sets as slots, hence the shifted mask.
        final int pos = (mix(key.hashCode()) & (mask >> 1)) << 1;
        if (key.equals(keys[pos])) {
            return (V) values[pos];
        }
        final var value = key.equals(keys[pos + 1]) ? (V) values[pos + 1] : mappingFunction.apply(key);
        keys[pos + 1] = keys[pos];
        values[pos + 1] = values[pos];
        keys[pos] = key;
        values[pos] = value;
        return value;
    }
}
