package eu.neverblink.jelly.core.memory;

import eu.neverblink.protoc.java.runtime.MessageFactory;
import eu.neverblink.protoc.java.runtime.ProtoMessage;

public final class HeapAllocator<T extends ProtoMessage<?>> implements MessageAllocator<T> {

    private final MessageFactory<T> factory;

    HeapAllocator(MessageFactory<T> factory) {
        this.factory = factory;
    }

    @Override
    public T newInstance() {
        return factory.create();
    }

    @Override
    public void releaseAll() {}
}
