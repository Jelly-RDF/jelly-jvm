package eu.ostrzyciel.jelly.core;

/**
 * A terrifyingly simple cache.
 */
abstract class EncoderNodeCache<V> {

    protected final Object[] keys;

    protected final int sizeMinusOne;

    protected EncoderNodeCache(int minimumSize) {
        var size = Integer.highestOneBit(minimumSize);
        if (size < minimumSize) {
            size <<= 1;
        }
        this.sizeMinusOne = size - 1;
        keys = new Object[size];
    }

    protected int calcIndex(Object key) {
        int h = key.hashCode();
        // Spread bits to avoid collisions for hashes that differ only in the upper bits.
        // Trick from HashMap.hash()
        return (h ^ h >>> 16) & sizeMinusOne;
    }
}
