package eu.neverblink.jelly.core.buffer;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

public final class ReusableRowBuffer extends AbstractCollection<RdfStreamRow> implements RowBuffer {

    private RdfStreamRow[] rows;
    private int visibleSize = 0;
    private int initializedSize = 0;
    private int capacity;

    /**
     * Package-private constructor.
     * Use RowBuffer.newReusableRowBuffer(int initialCapacity) instead.
     * @param initialCapacity initial capacity of the buffer
     */
    ReusableRowBuffer(int initialCapacity) {
        this.rows = new RdfStreamRow[initialCapacity];
        this.capacity = initialCapacity;
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
    public RdfStreamRow.Mutable appendRow() {
        if (visibleSize < initializedSize) {
            // Cast, because the only other alternative is to spill covariance / contravariance
            // considerations across the entire codebase and make everyone's lives miserable.
            final var row = (RdfStreamRow.Mutable) rows[visibleSize++];
            // Reset the cached size of the row, so that it can be reused.
            row.resetCachedSize();
            return row;
        } else if (visibleSize >= capacity) {
            // Resize the array to make room for more rows.
            capacity = (capacity * 3) / 2;
            final var newRows = new RdfStreamRow[capacity];
            System.arraycopy(rows, 0, newRows, 0, visibleSize);
            rows = newRows;
        }
        final var row = RdfStreamRow.newInstance();
        rows[visibleSize++] = row;
        initializedSize++;
        return row;
    }

    @Override
    public Collection<RdfStreamRow> getRows() {
        return this;
    }

    public void reset() {
        visibleSize = 0;
    }
}
