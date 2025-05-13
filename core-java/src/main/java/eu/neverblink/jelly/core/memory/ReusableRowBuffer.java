package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Buffer of RdfStreamRow messages, which will re-use the same internal array of RdfStreamRow
 * objects when it is cleared. You must NEVER keep a reference to the rows after calling clear(),
 * because they will be reused and their contents will be cleared.
 */
public final class ReusableRowBuffer extends AbstractCollection<RdfStreamRow> implements RowBuffer {

    private RdfStreamRow[] rows;
    private int visibleSize = 0;
    private int initializedSize = 0;
    private int capacity;
    private final Consumer<RdfStreamRow.Mutable> clearPolicy;

    /**
     * Package-private constructor.
     * Use RowBuffer.newReusableRowBuffer(int initialCapacity) instead.
     * @param initialCapacity initial capacity of the buffer
     * @param clearPolicy method to clear the row when it is reused
     */
    ReusableRowBuffer(int initialCapacity, Consumer<RdfStreamRow.Mutable> clearPolicy) {
        // Don't trust the user -- they *might* set this parameter to something very high,
        // which would result in needlessly large allocations.
        this.capacity = Math.min(initialCapacity, 2048);
        this.rows = new RdfStreamRow[capacity];
        this.clearPolicy = clearPolicy;
    }

    @Override
    public boolean isEmpty() {
        return visibleSize == 0;
    }

    @Override
    public Iterator<RdfStreamRow> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < visibleSize;
            }

            @Override
            public RdfStreamRow next() {
                return rows[index++];
            }
        };
    }

    @Override
    public int size() {
        return visibleSize;
    }

    @Override
    public RdfStreamRow.Mutable appendMessage() {
        if (visibleSize < initializedSize) {
            // Cast, because the only other alternative is to spill covariance / contravariance
            // considerations across the entire codebase and make everyone's lives miserable.
            final var row = (RdfStreamRow.Mutable) rows[visibleSize++];
            // Clear the row using the specified policy before returning, so that it can be reused.
            clearPolicy.accept(row);
            return row;
        } else if (visibleSize >= capacity) {
            // Resize the array to make room for more rows.
            capacity = (capacity * 3) / 2;
            final var newRows = new RdfStreamRow[capacity];
            System.arraycopy(rows, 0, newRows, 0, visibleSize);
            rows = newRows;
        }
        // Batch-allocate instances to avoid frequent allocations
        // and to hopefully improve cache locality.
        initializedSize = Math.min(capacity, visibleSize + 16);
        for (int i = visibleSize; i < initializedSize; i++) {
            rows[i] = RdfStreamRow.newInstance();
        }
        return (RdfStreamRow.Mutable) rows[visibleSize++];
    }

    @Override
    public Collection<RdfStreamRow> getRows() {
        return this;
    }

    @Override
    public void clear() {
        visibleSize = 0;
    }

    /**
     * For encoding, we just reset the cached size of the rows, because the contents will be
     * overwritten anyway.
     */
    static final Consumer<RdfStreamRow.Mutable> ENCODER_CLEAR_POLICY = RdfStreamRow::resetCachedSize;

    /**
     * For decoding, we don't even need to reset the cached size, as it's not used anyway.
     * The contents will be overwritten.
     */
    static final Consumer<RdfStreamRow.Mutable> DECODER_CLEAR_POLICY = row -> {};
}
