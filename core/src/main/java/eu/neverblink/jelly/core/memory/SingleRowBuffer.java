package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.*;
import java.util.function.Consumer;

/**
 * Buffer holding only a single RdfStreamRow message.
 * This can be used only in parsers, where the row after being parsed from the binary stream is
 * immediately passed to the consumer (ProtoDecoder). You must NEVER keep a reference to the row
 * after calling appendMessage() or clear(), because it will be reused and its contents will
 * be overwritten.
 */
public final class SingleRowBuffer extends AbstractCollection<RdfStreamRow> implements RowBuffer {

    private final RdfStreamRow.Mutable row = RdfStreamRow.newInstance();
    private boolean pendingRow = false;
    private final Consumer<RdfStreamRow> consumer;

    /**
     * Package-private constructor.
     * Use RowBuffer.newSingle(Consumer&lt;RdfStreamRow&gt;) instead.
     * @param consumer consumer to which the row will be passed
     */
    SingleRowBuffer(Consumer<RdfStreamRow> consumer) {
        this.consumer = consumer;
    }
    
    @Override
    public Collection<RdfStreamRow> getRows() {
        return this;
    }

    @Override
    public RdfStreamRow.Mutable appendMessage() {
        if (pendingRow) {
            // Send the previous row to the consumer
            consumer.accept(row);
        } else {
            pendingRow = true;
        }
        return row;
    }

    @Override
    public Iterator<RdfStreamRow> iterator() {
        if (!pendingRow) {
            return Collections.emptyIterator();
        } else {
            return new Iterator<>() {
                private boolean hasNext = true;

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public RdfStreamRow next() {
                    if (hasNext) {
                        hasNext = false;
                        return row;
                    }
                    throw new IllegalStateException("No more elements");
                }
            };
        }
    }

    @Override
    public int size() {
        return pendingRow ? 1 : 0;
    }

    @Override
    public void clear() {
        if (pendingRow) {
            // If there is a row waiting to be consumed, send it to the consumer
            consumer.accept(row);
            pendingRow = false;
        }
    }
}
