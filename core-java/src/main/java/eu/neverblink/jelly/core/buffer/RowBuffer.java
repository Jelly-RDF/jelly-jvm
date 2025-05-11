package eu.neverblink.jelly.core.buffer;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.List;

public interface RowBuffer {
    boolean isEmpty();

    int size();

    RdfStreamRow.Mutable appendRow();

    List<RdfStreamRow> getRows();

    static ReusableRowBuffer newReusable(int initialCapacity) {
        return new ReusableRowBuffer(initialCapacity);
    }

    static RowBuffer newLazyImmutable(int initialCapacity) {
        return new LazyImmutableRowBuffer(initialCapacity);
    }

    static RowBuffer newLazyImmutable() {
        return new LazyImmutableRowBuffer(16);
    }
}
