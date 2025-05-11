package eu.neverblink.jelly.core.buffer;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.ArrayList;
import java.util.List;

public final class LazyImmutableRowBuffer implements RowBuffer {

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
    public int size() {
        if (rows == null) {
            return 0;
        }
        return rows.size();
    }

    @Override
    public RdfStreamRow.Mutable appendRow() {
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
        }
        final var toReturn = rows;
        rows = null;
        return toReturn;
    }
}
