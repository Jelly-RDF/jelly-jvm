package eu.neverblink.protoc.java.runtime;

import java.util.Collection;

public interface MessageCollection<T, TMutable extends T> extends Collection<T> {
    TMutable appendMessage();
}
