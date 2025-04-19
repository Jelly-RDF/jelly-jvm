package eu.ostrzyciel.jelly.core.internal;

public class DecoderLookup<T> {

    private int lastSetId = -1;
    private final T[] lookup;

    @SuppressWarnings("unchecked")
    public DecoderLookup(int maxEntries) {
        this.lookup = (T[]) new Object[maxEntries];
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
