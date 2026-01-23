package eu.neverblink.protoc.java.runtime;

import java.util.ArrayList;

public final class ArrayListMessageCollection<T extends ProtoMessage<?>, TMutable extends T>
    extends ArrayList<T>
    implements MessageCollection<T, TMutable>
{

    private final MessageFactory<TMutable> factory;

    public ArrayListMessageCollection(MessageFactory<TMutable> factory) {
        super();
        this.factory = factory;
    }

    @Override
    public TMutable appendMessage() {
        final TMutable message = factory.create();
        add(message);
        return message;
    }
}
