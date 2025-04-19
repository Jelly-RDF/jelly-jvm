package eu.ostrzyciel.jelly.core;

public class JellyConstants {

    private JellyConstants() {}

    public static final String JELLY_NAME = "Jelly";
    public static final String JELLY_FILE_EXTENSION = "jelly";
    public static final String JELLY_CONTENT_TYPE = "application/x-jelly-rdf";

    public static final int PROTO_VERSION_1_0_X = 1;
    public static final int PROTO_VERSION_1_1_X = 2;
    public static final int PROTO_VERSION = PROTO_VERSION_1_1_X;

    public static final String PROTO_SEMANTIC_VERSION_1_0_0 = "1.0.0"; // First protocol version
    public static final String PROTO_SEMANTIC_VERSION_1_1_0 = "1.1.0"; // Protocol version with namespace declarations
    public static final String PROTO_SEMANTIC_VERSION_1_1_1 = "1.1.1"; // Protocol version with metadata in RdfStreamFrame
    public static final String PROTO_SEMANTIC_VERSION = PROTO_SEMANTIC_VERSION_1_1_1;
}
