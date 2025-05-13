package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.*;

/**
 * Buffer of RdfStreamRow messages, which will create a new internal array of RdfStreamRow
 * objects when it is cleared. The returned list of rows is immutable from the perspective of the
 * buffer -- it will not be modified after it is returned and can be safely passed around with
 * indefinite lifetime.
 */
public final class LazyImmutableRowBuffer extends AbstractCollection<RdfStreamRow> implements RowBuffer {

    private List<RdfStreamRow> rows = null;
    private int initialCapacity;

    /**
     * Package-private constructor.
     * Use RowBuffer.newLazyImmutable instead.
     * @param initialCapacity initial capacity of the buffer
     */
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
}
