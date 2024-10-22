package eu.ostrzyciel.jelly.core;

/**
 * A terrifyingly simple cache.
 *
 * TODO: modifications
 *
 * Code copied from Apache Jena 5.2.0:
 * https://github.com/apache/jena/blob/6443abda6e2717b95b05c45515817584e93ef244/jena-base/src/main/java/org/apache/jena/atlas/lib/cache/CacheSimple.java#L40
 *
 * TODO: license
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
abstract class EncoderNodeCache<K, V> {
    protected final Object[] keys;
    protected final int sizeMinusOne;

    protected EncoderNodeCache(int minimumSize) {
        var size = Integer.highestOneBit(minimumSize);
        if (size < minimumSize) {
            size <<= 1;
        }
        this.sizeMinusOne = size - 1;

        @SuppressWarnings("unchecked")
        K[] z = (K[]) new Object[size];
        keys = z;
    }

    protected int calcIndex(Object key) {
        return key.hashCode() & sizeMinusOne;
    }
}


