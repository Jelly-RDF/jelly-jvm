package eu.ostrzyciel.jelly.core;

import java.util.Objects;
import java.util.function.Function;

/**
 * A terrifyingly simple cache.
 *
 * Code copied from Apache Jena 5.2.0:
 * https://github.com/apache/jena/blob/6443abda6e2717b95b05c45515817584e93ef244/jena-base/src/main/java/org/apache/jena/atlas/lib/cache/CacheSimple.java#L40
 *
 * Authors:
 * - Andy Seaborne
 * - A. Soroka
 * - strangepleasures
 * - arne-bdt
 * https://github.com/apache/jena/commits/6443abda6e2717b95b05c45515817584e93ef244/jena-base/src/main/java/org/apache/jena/atlas/lib/cache/CacheSimple.java
 *
 * @param <K>
 * @param <V>
 */
final class EncoderNodeCache<K, V> {
    private final V[] values;
    private final K[] keys;
    private final int sizeMinusOne;
    // private int currentSize = 0;

    public EncoderNodeCache(int minimumSize) {
        var size = Integer.highestOneBit(minimumSize);
        if (size < minimumSize) {
            size <<= 1;
        }
        this.sizeMinusOne = size-1;

        @SuppressWarnings("unchecked")
        V[] x = (V[])new Object[size];
        values = x;

        @SuppressWarnings("unchecked")
        K[] z = (K[])new Object[size];
        keys = z;
    }

    private int calcIndex(K key) {
        return key.hashCode() & sizeMinusOne;
    }

    public V computeIfAbsent(K key, Function<K, V> function) {
        final int idx = calcIndex(key);
        final boolean isExistingKeyNotNull = keys[idx] != null;
        if (isExistingKeyNotNull && keys[idx].equals(key)) {
            return values[idx];
        } else {
            final var value = function.apply(key);
            if (value != null) {
                values[idx] = value;
//                if (!isExistingKeyNotNull) {
//                    currentSize++;
//                }
                keys[idx] = key;
            }
            return value;
        }
    }
}
