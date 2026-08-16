package eu.neverblink.protoc.java.runtime;

import java.util.Arrays;

/**
 * Growable store for repeated {@code int32}, {@code uint32}, {@code sint32}, {@code fixed32},
 * and {@code sfixed32} proto fields. Backed by a plain {@code int[]} to avoid boxing.
 */
public final class RepeatedInt {

    private static final int[] EMPTY_ARRAY = new int[0];
    private static final int DEFAULT_CAPACITY = 8;

    private int[] values = EMPTY_ARRAY;
    private int size = 0;

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
