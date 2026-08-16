package eu.neverblink.protoc.java.runtime;

import java.util.Arrays;

/**
 * Growable store for repeated {@code int64}, {@code uint64}, {@code sint64}, {@code fixed64},
 * and {@code sfixed64} proto fields. Backed by a plain {@code long[]} to avoid boxing.
 */
public final class RepeatedLong {

    private static final long[] EMPTY_ARRAY = new long[0];
    private static final int DEFAULT_CAPACITY = 8;

    private long[] values = EMPTY_ARRAY;
    private int size = 0;

    private RepeatedLong() {}

    public static RepeatedLong newEmptyInstance() {
        return new RepeatedLong();
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public long get(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index %d out of bounds for size %d".formatted(index, size));
        }
        return values[index];
    }

    public void add(long value) {
        reserve(1);
        values[size++] = value;
    }

    public void addAll(RepeatedLong other) {
        reserve(other.size);
        System.arraycopy(other.values, 0, values, size, other.size);
        size += other.size;
    }

    public void clear() {
        size = 0;
    }

    /**
     * Returns the backing array. It may be longer than {@link #size()}; the values past the
     * current size are undefined. The returned array is invalidated by the next {@code add} call.
     *
     * @return the backing array
     */
    public long[] array() {
        return values;
    }

    private void reserve(int count) {
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
        if (!(o instanceof RepeatedLong other) || other.size != size) {
            return false;
        }
        return Arrays.equals(values, 0, size, other.values, 0, size);
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < size; i++) {
            result = 31 * result + Long.hashCode(values[i]);
        }
        return result;
    }
}
