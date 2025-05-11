package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import java.util.*;

public final class LazyImmutableRowBuffer extends AbstractCollection<RdfStreamRow> implements RowBuffer {

    private List<RdfStreamRow> rows = null;
    private int initialCapacity;

    LazyImmutableRowBuffer(int initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    @Override
    public boolean isEmpty() {
        return rows == null || rows.isEmpty();
    }

    @Override
    public Iterator<RdfStreamRow> iterator() {
        if (rows == null) {
            return Collections.emptyIterator();
        }
        return rows.iterator();
    }

    @Override
    public int size() {
        if (rows == null) {
            return 0;
        }
        return rows.size();
    }

    @Override
    public RdfStreamRow.Mutable appendMessage() {
        if (rows == null) {
            rows = new ArrayList<>(initialCapacity);
        }
        final var row = RdfStreamRow.newInstance();
        rows.add(row);
        return row;
    }

    @Override
    public List<RdfStreamRow> getRows() {
        if (rows != null && rows.size() > initialCapacity) {
            // Increase the initial capacity to the size of the rows + 8, so that next time we
            // create a new buffer, we don't have to resize it.
            initialCapacity = rows.size() + 8;
        } else if (rows == null) {
            return Collections.emptyList();
        }
        final var toReturn = rows;
        rows = null;
        return toReturn;
    }

    @Override
    public RdfTriple.Mutable newTriple() {
        return TRIPLE_ALLOCATOR.newInstance();
    }

    @Override
    public RdfQuad.Mutable newQuad() {
        return QUAD_ALLOCATOR.newInstance();
    }

    private static final MessageAllocator<RdfTriple.Mutable> TRIPLE_ALLOCATOR = MessageAllocator.heapAllocator(
        RdfTriple::newInstance
    );

    private static final MessageAllocator<RdfQuad.Mutable> QUAD_ALLOCATOR = MessageAllocator.heapAllocator(
        RdfQuad::newInstance
    );
}
