package eu.neverblink.jelly.core.memory;

import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Buffer of RdfStreamRow messages, used both by the encoder and decoder.
 * This can be either a reusable buffer (allocation-free), or a lazy immutable buffer.
 * Use the first one if you can guarantee that the proto objects are only created temporarily
 * for a single frame, and then never used again. This happens for example in Jena/RDF4J writers
 * and readers. Otherwise, use the lazy immutable buffer, it never reuses the proto objects.
 */
public interface RowBuffer extends MessageCollection<RdfStreamRow, RdfStreamRow.Mutable> {
    /**
     * Returns true if the buffer is empty.
     * @return true if the buffer is empty, false otherwise
     */
    boolean isEmpty();

    /**
     * Returns the number of rows in the buffer.
     * @return current size
     */
    int size();

    /**
     * Returns a collection of rows and clears the buffer.
     * @return iterator
     */
    Collection<RdfStreamRow> getRows();

    /**
     * Creates a new ReusableRowBuffer with the given initial capacity, for use by the ProtoEncoder.
     * This buffer maintains internally a single array of RdfStreamRow objects, and reuses them
     * for each frame (after clear() is called).
     * @param initialCapacity initial capacity of the buffer
     * @return a new ReusableRowBuffer for encoding
     */
    static ReusableRowBuffer newReusableForEncoder(int initialCapacity) {
        return new ReusableRowBuffer(initialCapacity, ReusableRowBuffer.ENCODER_CLEAR_POLICY);
    }

    /**
     * Creates a new ReusableRowBuffer with the given initial capacity, for use by the ProtoDecoder.
     * This buffer maintains internally a single array of RdfStreamRow objects, and reuses them
     * for each frame (after clear() is called).
     * @param initialCapacity initial capacity of the buffer
     * @return a new ReusableRowBuffer for decoding
     */
    static ReusableRowBuffer newReusableForDecoder(int initialCapacity) {
        return new ReusableRowBuffer(initialCapacity, ReusableRowBuffer.DECODER_CLEAR_POLICY);
    }

    /**
     * Creates a new LazyImmutableRowBuffer, for decoding or encoding.
     * After getRows() or clear() is called, it will completely recreate the buffer and allocate
     * new RdfStreamRow objects for you.
     * It won't allocate anything, until you call appendMessage().
     * You should use this if you are not sure about the lifetimes of your RdfStreamRow objects.
     * @param initialCapacity initial capacity of the buffer
     * @return a new LazyImmutableRowBuffer
     */
    static LazyImmutableRowBuffer newLazyImmutable(int initialCapacity) {
        return new LazyImmutableRowBuffer(initialCapacity);
    }

    /**
     * Creates a new LazyImmutableRowBuffer with the default initial capacity of 16.
     * @return a new LazyImmutableRowBuffer
     */
    static LazyImmutableRowBuffer newLazyImmutable() {
        return new LazyImmutableRowBuffer(16);
    }

    /**
     * Creates a new SingleRowBuffer, for decoding only.
     * This buffer will only hold a single row at a time. This row will be passed to the consumer
     * when appendMessage() is called for the next row, or when clear() is called.
     * <p>
     * To use this correctly, you must: (1) set the consumer to ProtoDecoder::ingestRow,
     * (2) call clear() at the end of each frame, and (3) never maintain a reference to the row
     * that is passed to the consumer.
     * <p>
     * The advantage of this buffer is that it only ever allocates a single RdfStreamRow object,
     * resulting in a very low memory footprint. As compared to the reusable buffer, it has
     * better cache locality.
     * @param consumer consumer to which the row will be passed
     * @return a new SingleRowBuffer
     */
    static RowBuffer newSingle(Consumer<RdfStreamRow> consumer) {
        return new SingleRowBuffer(consumer);
    }
}
