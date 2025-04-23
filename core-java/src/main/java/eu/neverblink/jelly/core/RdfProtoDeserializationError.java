package eu.neverblink.jelly.core;

/**
 * This exception is thrown when there is an error during the deserialization of a
 * protocol buffer message from RDF.
 */
public final class RdfProtoDeserializationError extends RuntimeException {

    public RdfProtoDeserializationError(String msg) {
        super(msg);
    }

    public RdfProtoDeserializationError(String msg, Throwable cause) {
        super(msg, cause);
    }
}
