package eu.neverblink.protoc.java.runtime;

import com.google.protobuf.CodedOutputStream;
import eu.neverblink.jelly.core.InternalApi;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Growable store for repeated {@code string} proto fields.
 */
public final class RepeatedString implements Iterable<String> {

    private static final String[] EMPTY_ARRAY = new String[0];
    private static final int DEFAULT_CAPACITY = 8;

    private String[] values = EMPTY_ARRAY;
    private int size = 0;

    /**
     * The values in UTF-8, as {@link #computeStringSizeNoTag} made them for {@link #utf8}: measuring a
     * string and writing it then take one vectorised {@link String#getBytes} instead of two or
     * three passes over its characters. Null where a value was added since.
     */
    private byte[][] utf8 = null;

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
        final byte[][] utf8 = this.utf8;
        if (utf8 != null && size < utf8.length) {
            utf8[size] = null;
        }
        values[size++] = value.toString();
    }

    public void addAll(RepeatedString other) {
        reserve(other.size);
        forgetUtf8(size, size + other.size);
        System.arraycopy(other.values, 0, values, size, other.size);
        size += other.size;
    }

    public void clear() {
        // Null out the references to allow garbage collection
        Arrays.fill(values, 0, size, null);
        forgetUtf8(0, size);
        size = 0;
    }

    /**
     * The size of the values in the wire format, without their tags: each one's length and its
     * UTF-8 bytes. Keeps the bytes for {@link #utf8}.
     */
    @InternalApi
    public int computeStringSizeNoTag() {
        if (utf8 == null || utf8.length < size) {
            utf8 = new byte[values.length][];
        }
        int dataSize = 0;
        for (int i = 0; i < size; i++) {
            final byte[] bytes = values[i].getBytes(StandardCharsets.UTF_8);
            utf8[i] = bytes;
            dataSize += CodedOutputStream.computeUInt32SizeNoTag(bytes.length) + bytes.length;
        }
        return dataSize;
    }

    /**
     * A value in UTF-8, the same bytes as {@link CodedOutputStream#writeStringNoTag} writes. Taken
     * from the last {@link #computeStringSizeNoTag} if it covered this value.
     */
    public byte[] utf8(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index %d out of bounds for size %d".formatted(index, size));
        }
        final byte[] bytes = utf8 == null || index >= utf8.length ? null : utf8[index];
        return bytes != null ? bytes : values[index].getBytes(StandardCharsets.UTF_8);
    }

    private void forgetUtf8(int from, int to) {
        if (utf8 != null && from < utf8.length) {
            Arrays.fill(utf8, from, Math.min(to, utf8.length), null);
        }
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
