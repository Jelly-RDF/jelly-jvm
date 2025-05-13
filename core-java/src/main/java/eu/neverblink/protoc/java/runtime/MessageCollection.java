package eu.neverblink.protoc.java.runtime;

import java.util.Collection;

/**
 * A collection of ProtoMessage objects.
 * @param <T> The type of ProtoMessage.
 * @param <TMutable> T.Mutable subclass
 */
public interface MessageCollection<T, TMutable extends T> extends Collection<T> {
    /**
     * Create a new message of the same type as this collection and add it to the collection.
     * @return the newly created message
     */
    TMutable appendMessage();
}
