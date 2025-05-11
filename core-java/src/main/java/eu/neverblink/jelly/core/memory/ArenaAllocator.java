package eu.neverblink.jelly.core.memory;

import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;

public final class ArenaAllocator<T extends ProtoMessage<?>> implements MessageAllocator<T> {

    private final MessageFactory<T> factory;
    private final int maxSize;
    // Lazy-initialize the buffer to avoid unnecessary memory allocation if this message
    // type is never used.
    private T[] buffer = null;
    private int used = 0;

    ArenaAllocator(MessageFactory<T> factory, int maxSize) {
        this.factory = factory;
        this.maxSize = maxSize;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T newInstance() {
        if (buffer == null) {
            // Initialize the buffer with the maximum size
            buffer = (T[]) new ProtoMessage<?>[maxSize];
        }
        if (used >= maxSize) {
            // No more space in the arena, allocate a new instance on the heap
            return factory.create();
        }
        if (buffer[used] == null) {
            // Batch-allocate instances to avoid frequent allocations
            // and to hopefully improve cache locality.
            for (int i = used; i < Math.min(maxSize, used + 16); i++) {
                buffer[i] = factory.create();
            }
            return buffer[used++];
        } else {
            T instance = buffer[used++];
            instance.clear();
            return instance;
        }
    }

    @Override
    public void releaseAll() {
        used = 0;
    }
}
