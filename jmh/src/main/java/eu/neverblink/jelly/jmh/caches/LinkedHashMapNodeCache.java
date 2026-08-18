package eu.neverblink.jelly.jmh.caches;

import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * Insertion-ordered rather than access-ordered (LinkedHashMap's default), so it evicts FIFO, not LRU.
 */
public final class LinkedHashMapNodeCache<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    public LinkedHashMapNodeCache(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }

    /**
     * Named differently from Map.computeIfAbsent so that the benchmark call site cannot accidentally
     * bind to the interface method and lose the chance to inline.
     */
    public V getOrCompute(K key, Function<K, V> mappingFunction) {
        return computeIfAbsent(key, mappingFunction);
    }
}
