package eu.neverblink.jelly.core;

/**
 * Exception thrown when an error occurs during the transcoding of RDF ProtoBuf data.
 */
public final class RdfProtoTranscodingError extends RuntimeException {

    public RdfProtoTranscodingError(String msg) {
        super(msg);
    }

    public RdfProtoTranscodingError(String msg, Throwable cause) {
        super(msg, cause);
    }
}
