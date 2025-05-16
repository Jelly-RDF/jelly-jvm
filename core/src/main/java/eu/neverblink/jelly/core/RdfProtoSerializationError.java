package eu.neverblink.jelly.core;

/**
 * This exception is thrown when there is an error during the serialization of a
 * protocol buffer message to RDF.
 */
public final class RdfProtoSerializationError extends RuntimeException {

    public RdfProtoSerializationError(String msg) {
        super(msg);
    }

    public RdfProtoSerializationError(String msg, Throwable cause) {
        super(msg, cause);
    }
}
