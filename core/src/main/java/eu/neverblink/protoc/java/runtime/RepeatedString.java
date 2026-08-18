package eu.neverblink.protoc.java.runtime;

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
        values[size++] = value.toString();
    }

    public void addAll(RepeatedString other) {
        reserve(other.size);
        System.arraycopy(other.values, 0, values, size, other.size);
        size += other.size;
    }

    public void clear() {
        // Null out the references to allow garbage collection
        Arrays.fill(values, 0, size, null);
        size = 0;
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
