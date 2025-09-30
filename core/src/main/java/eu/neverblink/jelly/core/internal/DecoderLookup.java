package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;

/**
 * Simple, array-based lookup for the protobuf decoder.
 * @param <T> type of the value
 */
@InternalApi
public final class DecoderLookup<T> {

    private int lastSetId = -1;
    private final T[] lookup;

    /**
     * Create a new decoder lookup table.
     * @param maxEntries maximum number of entries
     */
    @SuppressWarnings("unchecked")
    public DecoderLookup(int maxEntries) {
        this.lookup = (T[]) new Object[maxEntries];
    }

    /**
     * @param id 1-based. 0 signifies an id that is larger by 1 than the last set id.
     * @param v value
     * @throws ArrayIndexOutOfBoundsException if id &lt; 0 or id &gt; maxEntries
     */
    public void update(int id, T v) {
        if (id == 0) {
            lastSetId += 1;
        } else {
            lastSetId = id - 1;
        }

        lookup[lastSetId] = v;
    }

    /**
     * @param id 1-based
     * @return value
     * @throws ArrayIndexOutOfBoundsException if id &lt; 1 or id &gt; maxEntries
     */
    public T get(int id) {
        return lookup[id - 1];
    }
}
