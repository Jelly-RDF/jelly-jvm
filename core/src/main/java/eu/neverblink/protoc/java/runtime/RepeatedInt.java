package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.InternalApi;
import java.io.IOException;
import java.util.Arrays;

/**
 * Growable store for repeated {@code int32}, {@code uint32}, {@code sint32}, {@code fixed32},
 * and {@code sfixed32} proto fields. Backed by a plain {@code int[]} to avoid boxing.
 */
public final class RepeatedInt {

    private static final int[] EMPTY_ARRAY = new int[0];
    private static final byte[] EMPTY_BYTES = new byte[0];
    // The most bytes a uint32 varint takes
    private static final int MAX_VARINT_SIZE = 5;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    private static final int DEFAULT_CAPACITY = 8;

    // Package-private for ProtobufUtil.readPackedUInt32, which fills the array directly
    int[] values = EMPTY_ARRAY;
    int size = 0;

    // The size of the values as packed uint32 varints, and how many values that was for.
    private int uint32Size;
    private int uint32SizeCount = -1;
    // The varints themselves, the first uint32Size bytes, made while measuring them. Writing them
    // is then one copy. Kept between measurements to be reused.
    private byte[] packed = EMPTY_BYTES;

    private RepeatedInt() {}

    public static RepeatedInt newEmptyInstance() {
        return new RepeatedInt();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int get(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index %d out of bounds for size %d".formatted(index, size));
        }
        return values[index];
    }

    public void add(int value) {
        reserve(1);
        values[size++] = value;
    }

    public void addAll(RepeatedInt other) {
        reserve(other.size);
        System.arraycopy(other.values, 0, values, size, other.size);
        size += other.size;
    }

    public void clear() {
        size = 0;
        uint32SizeCount = -1;
    }

    /**
     * The size of the values as uint32 varints, without a tag or a length. Encodes the varints on
     * the way, for {@link #writePackedNoTag}.
     */
    @InternalApi
    public int computeUInt32SizeNoTag() {
        final int[] array = values;
        final int size = this.size;
        byte[] out = packed;
        final long worstCase = (long) MAX_VARINT_SIZE * size;
        if (out.length < worstCase) {
            out = packed = new byte[worstCase <= MAX_ARRAY_SIZE ? Math.max((int) worstCase, 64) : exactUInt32Size()];
        }
        // A local position, so that it stays in a register: CodedOutputStream keeps its position
        // in a field, which costs a load and a store for every byte.
        int pos = 0;
        for (int i = 0; i < size; i++) {
            int value = array[i];
            while ((value & ~0x7F) != 0) {
                out[pos++] = (byte) (value | 0x80);
                value >>>= 7;
            }
            out[pos++] = (byte) value;
        }
        uint32Size = pos;
        uint32SizeCount = size;
        return pos;
    }

    // Too many values to make room for the largest varints: measure them first
    private int exactUInt32Size() {
        int dataSize = 0;
        for (int i = 0; i < size; i++) {
            dataSize += CodedOutputStream.computeUInt32SizeNoTag(values[i]);
        }
        return dataSize;
    }

    /** The same as {@link #computeUInt32SizeNoTag()}, measured again only if values were added. */
    int uint32SizeNoTag() {
        return uint32SizeCount == size ? uint32Size : computeUInt32SizeNoTag();
    }

    /**
     * Writes the values as a packed field without its tag: the length, then the varints, as
     * {@link #computeUInt32SizeNoTag()} encoded them (encoded again only if values were added).
     */
    void writePackedNoTag(CodedOutputStream output) throws IOException {
        final int length = uint32SizeNoTag();
        output.writeUInt32NoTag(length);
        output.writeRawBytes(packed, 0, length);
    }

    /**
     * Returns the backing array. It may be longer than {@link #size()}; the values past the
     * current size are undefined. The returned array is invalidated by the next {@code add} call.
     * <p>
     * Values changed through it after the message was measured are not written: the values are
     * encoded when measured, as the message itself is written with its cached size.
     *
     * @return the backing array
     */
    public int[] array() {
        return values;
    }

    /**
     * Makes room for {@code count} more values, so that adding them does not grow the backing
     * array again.
     *
     * @param count the number of values that will be added
     */
    public void reserve(int count) {
        final int needed = size + count;
        if (needed > values.length) {
            values = Arrays.copyOf(values, Math.max(Math.max(DEFAULT_CAPACITY, needed), values.length * 2));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RepeatedInt other) || other.size != size) {
            return false;
        }
        return Arrays.equals(values, 0, size, other.values, 0, size);
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size; i++) {
            result = 31 * result + values[i];
        }
        return result;
    }
}
