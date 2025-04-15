package eu.ostrzyciel.jelly.core;

public sealed class JellyException extends RuntimeException {

    public static JellyException rdfProtoDeserializationError(String msg) {
        return new RdfProtoDeserializationError(msg);
    }

    public static JellyException rdfProtoSerializationError(String msg) {
        return new RdfProtoSerializationError(msg);
    }

    public static JellyException rdfProtoTranscodingError(String msg) {
        return new RdfProtoTranscodingError(msg);
    }

    public JellyException(String message) {
        super(message);
    }

    public static final class RdfProtoDeserializationError extends JellyException {
        public RdfProtoDeserializationError(String msg) {
            super(msg);
        }
    }

    public static final class RdfProtoSerializationError extends JellyException {
        public RdfProtoSerializationError(String msg) {
            super(msg);
        }
    }

    public static final class RdfProtoTranscodingError extends JellyException {
        public RdfProtoTranscodingError(String msg) {
            super(msg);
        }
    }
}
