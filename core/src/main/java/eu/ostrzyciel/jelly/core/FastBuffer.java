package eu.ostrzyciel.jelly.core;

class FastBuffer<T> {
    private static final int CAPACITY_INCREASE = 16;

    private T[] buffer;
    private int size;
    private int capacity;

    public FastBuffer(int capacity) {
        this.capacity = capacity;
        this.size = 0;
        this.buffer = (T[]) new Object[capacity];
    }

    public FastBuffer<T> append(T element) {
        if (size == capacity) {
            capacity += CAPACITY_INCREASE;
            T[] newBuffer = (T[]) new Object[capacity];
            System.arraycopy(buffer, 0, newBuffer, 0, size);
            buffer = newBuffer;
        }
        buffer[size++] = element;
        return this;
    }

    public T get(int index) {
        return buffer[index];
    }

    public FastBuffer<T> clear() {
        size = 0;
        return this;
    }

    public T[] getBufferCopy() {
        T[] copy = (T[]) new Object[size];
        System.arraycopy(buffer, 0, copy, 0, size);
        return copy;
    }

    public int size() {
        return size;
    }
}
