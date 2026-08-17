package eu.neverblink.jelly.jmh.caches;

import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * LinkedHashMap in accessOrder mode, so eviction is by least recently used.
 */
public final class LinkedHashMapLruNodeCache<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    public LinkedHashMapLruNodeCache(int maxSize) {
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }

    public V getOrCompute(K key, Function<K, V> mappingFunction) {
        return computeIfAbsent(key, mappingFunction);
    }
}
