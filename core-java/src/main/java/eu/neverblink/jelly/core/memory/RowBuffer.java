package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import java.util.Collection;

public interface RowBuffer {
    boolean isEmpty();

    int size();

    RdfStreamRow.Mutable appendRow();

    Collection<RdfStreamRow> getRows();

    RdfTriple.Mutable newTriple();

    RdfQuad.Mutable newQuad();

    static ReusableRowBuffer newReusable(int initialCapacity) {
        return new ReusableRowBuffer(initialCapacity);
    }

    static LazyImmutableRowBuffer newLazyImmutable(int initialCapacity) {
        return new LazyImmutableRowBuffer(initialCapacity);
    }

    static LazyImmutableRowBuffer newLazyImmutable() {
        return new LazyImmutableRowBuffer(16);
    }
}
