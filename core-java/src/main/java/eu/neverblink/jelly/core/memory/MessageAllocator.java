package eu.neverblink.jelly.core.memory;

import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;

public interface MessageAllocator<T extends ProtoMessage<?>> {
    T newInstance();

    void releaseAll();

    static <T extends ProtoMessage<?>> MessageAllocator<T> heapAllocator(MessageFactory<T> factory) {
        return new HeapAllocator<>(factory);
    }

    static <T extends ProtoMessage<?>> MessageAllocator<T> arenaAllocator(MessageFactory<T> factory, int maxSize) {
        return new ArenaAllocator<>(factory, maxSize);
    }
}
