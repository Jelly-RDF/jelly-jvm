package eu.ostrzyciel.jelly.core;

public final class RdfProtoDeserializationError extends RuntimeException {

    public RdfProtoDeserializationError(String msg) {
        super(msg);
    }

    public RdfProtoDeserializationError(String msg, Throwable cause) {
        super(msg, cause);
    }
}
