package eu.neverblink.jelly.core.buffer;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;

import java.util.List;
import java.util.Vector;

public final class ReusableRowBuffer implements RowBuffer {

    private final Vector<RdfStreamRow> rows;
    private int size = 0;

    /**
     * Package-private constructor.
     * Use RowBuffer.newReusableRowBuffer(int initialCapacity) instead.
     * @param initialCapacity initial capacity of the buffer
     */
    ReusableRowBuffer(int initialCapacity) {
        this.rows = new Vector<>(initialCapacity);
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public RdfStreamRow.Mutable appendRow() {
        if (size < rows.size()) {
            // Cast, because the only other alternative is to spill covariance / contravariance
            // considerations across the entire codebase and make everyone's lives miserable.
            final var row = (RdfStreamRow.Mutable) rows.get(size++);
            // Reset the cached size of the row, so that it can be reused.
            row.resetCachedSize();
            return row;
        }
        final var row = RdfStreamRow.newInstance();
        rows.add(row);
        size++;
        return row;
    }

    @Override
    public List<RdfStreamRow> getRows() {
        rows.setSize(size);
        return rows;
    }

    public void reset() {
        size = 0;
    }
}
