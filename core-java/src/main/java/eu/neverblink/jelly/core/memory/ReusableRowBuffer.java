package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;

public final class ReusableRowBuffer extends AbstractCollection<RdfStreamRow> implements RowBuffer {

    private RdfStreamRow[] rows;
    private int visibleSize = 0;
    private int initializedSize = 0;
    private int capacity;
    
    private final MessageAllocator<RdfTriple.Mutable> tripleAllocator;
    private final MessageAllocator<RdfQuad.Mutable> quadAllocator;

    /**
     * Package-private constructor.
     * Use RowBuffer.newReusableRowBuffer(int initialCapacity) instead.
     * @param initialCapacity initial capacity of the buffer
     */
    ReusableRowBuffer(int initialCapacity) {
        // Don't trust the user -- they *might* set this parameter to something very high,
        // which would result in needlessly large allocations.
        this.capacity = Math.min(initialCapacity, 2048);
        this.rows = new RdfStreamRow[capacity];
        this.tripleAllocator = MessageAllocator.arenaAllocator(RdfTriple::newInstance, capacity);
        this.quadAllocator = MessageAllocator.arenaAllocator(RdfQuad::newInstance, capacity);
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

    @Override
    public RdfTriple.Mutable newTriple() {
        return tripleAllocator.newInstance();
    }

    @Override
    public RdfQuad.Mutable newQuad() {
        return quadAllocator.newInstance();
    }

    public void reset() {
        visibleSize = 0;
        tripleAllocator.releaseAll();
        quadAllocator.releaseAll();
    }
}
