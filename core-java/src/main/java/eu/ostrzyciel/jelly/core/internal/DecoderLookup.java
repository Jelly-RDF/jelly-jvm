package eu.ostrzyciel.jelly.core.internal;

import java.lang.reflect.Array;

public class DecoderLookup<T> {

    private int lastSetId = -1;
    private final T[] lookup;

    @SuppressWarnings("unchecked")
    public DecoderLookup(Class<T> type, int maxEntries) {
        this.lookup = (T[]) Array.newInstance(type, maxEntries);
    }

    public void update(int id, T v) {
        if (id == 0) {
            lastSetId += 1;
        } else {
            lastSetId = id - 1;
        }

        lookup[lastSetId] = v;
    }

    public T get(int id) {
        return lookup[id - 1];
    }
}
