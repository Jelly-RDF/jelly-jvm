package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;

/**
 * Simple, array-based lookup for the protobuf decoder.
 * <p>
 * This is only used for the datatype decoder. Name and prefix tables are in
 * {@link NameDecoderImpl}, which has to keep more per-entry state than this.
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
     * @throws RdfProtoDeserializationError if the identifier is out of bounds
     */
    public void update(int id, T v) {
        if (id == 0) {
            lastSetId += 1;
        } else {
            lastSetId = id - 1;
        }

        try {
            lookup[lastSetId] = v;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RdfProtoDeserializationError(
                "Datatype entry with ID %d is out of bounds of the datatype lookup table.".formatted(id)
            );
        }
    }

    /**
     * @param id 1-based
     * @return value
     * @throws RdfProtoDeserializationError if the identifier is out of bounds, or if the entry it
     *         points to was never set
     */
    public T get(int id) {
        final T value;
        try {
            value = lookup[id - 1];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RdfProtoDeserializationError(
                "Encountered an invalid datatype table reference (out of bounds). Datatype ID: %d".formatted(id)
            );
        }
        if (value == null) {
            throw new RdfProtoDeserializationError(
                "Encountered an invalid datatype table reference. Datatype ID: %d".formatted(id)
            );
        }
        return value;
    }
}
