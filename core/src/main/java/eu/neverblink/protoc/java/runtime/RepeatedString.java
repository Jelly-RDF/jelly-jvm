package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.InternalApi;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Growable store for repeated {@code string} proto fields.
 */
public final class RepeatedString implements Iterable<String> {

    private static final String[] EMPTY_ARRAY = new String[0];
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final int DEFAULT_CAPACITY = 8;
    private static final int MAX_VARINT_SIZE = 5;

    private String[] values = EMPTY_ARRAY;
    private int size = 0;

    /**
     * The values as a field of the wire format, as {@link #computeStringSizeWithTag} encoded them
     * for {@link #writeWithTag}: the tag, the length and the UTF-8 bytes of each value, one after
     * another. Written with one copy instead of three calls into the output per value.
     * {@code encodedLength} is -1 where values were added or removed since.
     */
    private byte[] encoded = EMPTY_BYTES;
    private int encodedLength = -1;
    private int encodedTag;

    private RepeatedString() {}

    public static RepeatedString newEmptyInstance() {
        return new RepeatedString();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public String get(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index %d out of bounds for size %d".formatted(index, size));
        }
        return values[index];
    }

    public void add(CharSequence value) {
        reserve(1);
        encodedLength = -1;
        values[size++] = value.toString();
    }

    public void addAll(RepeatedString other) {
        reserve(other.size);
        encodedLength = -1;
        System.arraycopy(other.values, 0, values, size, other.size);
        size += other.size;
    }

    public void clear() {
        // Null out the references to allow garbage collection
        Arrays.fill(values, 0, size, null);
        encodedLength = -1;
        size = 0;
    }

    /**
     * The size of the values in the wire format, without their tags: each one's length and its
     * UTF-8 bytes.
     */
    @InternalApi
    public int computeStringSizeNoTag() {
        int dataSize = 0;
        for (int i = 0; i < size; i++) {
            dataSize += CodedOutputStream.computeStringSizeNoTag(values[i]);
        }
        return dataSize;
    }

    /**
     * The size of the values as a non-packed field with the given tag: the tag, the length and the
     * UTF-8 bytes of each value. Encodes them on the way, for {@link #writeWithTag}.
     *
     * @param tag the field's tag, with its wire type
     */
    @InternalApi
    public int computeStringSizeWithTag(int tag) {
        final int tagSize = CodedOutputStream.computeUInt32SizeNoTag(tag);
        final String[] array = values;
        final int size = this.size;
        byte[] out = encoded;
        // A local position, so that it stays in a register
        int pos = 0;
        for (int i = 0; i < size; i++) {
            // String.getBytes is a vectorised copy for the common Latin-1 strings, and also writes
            // unpaired surrogates as '?', as protobuf does
            final byte[] bytes = array[i].getBytes(StandardCharsets.UTF_8);
            final int length = bytes.length;
            final int needed = pos + tagSize + MAX_VARINT_SIZE + length;
            if (needed > out.length) {
                out = Arrays.copyOf(out, Math.max(needed, Math.min(out.length * 2, Integer.MAX_VALUE - 8)));
            }
            pos = writeVarint(out, pos, tag);
            pos = writeVarint(out, pos, length);
            System.arraycopy(bytes, 0, out, pos, length);
            pos += length;
        }
        encoded = out;
        encodedTag = tag;
        encodedLength = pos;
        return pos;
    }

    /**
     * Writes the values as a non-packed field with the given tag, as
     * {@link #computeStringSizeWithTag} encoded them (encoded again if values were changed).
     *
     * @param output the output to write to
     * @param tag the field's tag, with its wire type
     * @throws IOException if an error occurred writing to {@code output}
     */
    @InternalApi
    public void writeWithTag(CodedOutputStream output, int tag) throws IOException {
        final byte[] out = encoded;
        final int length = encodedLength;
        if (length >= 0 && encodedTag == tag && length <= out.length) {
            output.writeRawBytes(out, 0, length);
            return;
        }
        for (int i = 0; i < size; i++) {
            output.writeUInt32NoTag(tag);
            output.writeStringNoTag(values[i]);
        }
    }

    private static int writeVarint(byte[] out, int pos, int value) {
        while ((value & ~0x7F) != 0) {
            out[pos++] = (byte) (value | 0x80);
            value >>>= 7;
        }
        out[pos++] = (byte) value;
        return pos;
    }

    private void reserve(int count) {
        final int needed = size + count;
        if (needed > values.length) {
            values = Arrays.copyOf(values, Math.max(Math.max(DEFAULT_CAPACITY, needed), values.length * 2));
        }
    }

    @Override
    public Iterator<String> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public String next() {
                if (index >= size) {
                    throw new NoSuchElementException();
                }
                return values[index++];
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RepeatedString other) || other.size != size) {
            return false;
        }
        return Arrays.equals(values, 0, size, other.values, 0, size);
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size; i++) {
            result = 31 * result + values[i].hashCode();
        }
        return result;
    }
}
