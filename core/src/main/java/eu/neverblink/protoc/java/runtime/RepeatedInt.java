package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import java.util.Arrays;

/**
 * Growable store for repeated {@code int32}, {@code uint32}, {@code sint32}, {@code fixed32},
 * and {@code sfixed32} proto fields. Backed by a plain {@code int[]} to avoid boxing.
 */
public final class RepeatedInt {

    private static final int[] EMPTY_ARRAY = new int[0];
    private static final int DEFAULT_CAPACITY = 8;

    // Package-private for ProtobufUtil.readPackedUInt32, which fills the array directly
    int[] values = EMPTY_ARRAY;
    int size = 0;

    // The size of the values as packed uint32 varints, and how many values that was for: the last
    // computeSerializedSize measured them, and writing them needs the same number again. Adding a
    // value changes size, so it is not stored on every add; clear() resets it.
    private int uint32Size;
    private int uint32SizeCount = -1;

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
     * The size of the values as uint32 varints, without a tag or a length. Kept for
     * {@link #uint32SizeNoTag()}.
     */
    int computeUInt32SizeNoTag() {
        final int[] array = values;
        final int size = this.size;
        int dataSize = 0;
        for (int i = 0; i < size; i++) {
            dataSize += CodedOutputStream.computeUInt32SizeNoTag(array[i]);
        }
        uint32Size = dataSize;
        uint32SizeCount = size;
        return dataSize;
    }

    /** The same as {@link #computeUInt32SizeNoTag()}, measured again only if values were added. */
    int uint32SizeNoTag() {
        return uint32SizeCount == size ? uint32Size : computeUInt32SizeNoTag();
    }

    /**
     * Returns the backing array. It may be longer than {@link #size()}; the values past the
     * current size are undefined. The returned array is invalidated by the next {@code add} call.
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
