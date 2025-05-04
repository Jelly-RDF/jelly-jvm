package eu.neverblink.protoc.java.runtime;

/**
 * Factory interface for creating messages
 *
 * @author Florian Enner
 * @since 15 Aug 2019
 */
public interface MessageFactory<T extends ProtoMessage<T>> {
    T create();
}
