package eu.neverblink.jelly.core;

public final class JellyConstants {

    private JellyConstants() {}

    public static final String JELLY_NAME = "Jelly";
    public static final String JELLY_FILE_EXTENSION = "jelly";
    public static final String JELLY_CONTENT_TYPE = "application/x-jelly-rdf";

    public static final int PROTO_VERSION_1_0_X = 1;
    public static final int PROTO_VERSION_1_1_X = 2;
    public static final int PROTO_VERSION_1_2_X = 3;
    public static final int PROTO_VERSION = PROTO_VERSION_1_2_X;

    public static final String PROTO_SEMANTIC_VERSION_1_0_0 = "1.0.0"; // First protocol version
    public static final String PROTO_SEMANTIC_VERSION_1_1_0 = "1.1.0"; // Protocol version with namespace declarations
    public static final String PROTO_SEMANTIC_VERSION_1_1_1 = "1.1.1"; // Protocol version with metadata in RdfStreamFrame
    public static final String PROTO_SEMANTIC_VERSION_1_2_0 = "1.2.0"; // Protocol version with the column layout and RDF 1.2
    public static final String PROTO_SEMANTIC_VERSION = PROTO_SEMANTIC_VERSION_1_2_0;

    /**
     * Whether a stream with the given protocol version uses the row layout (Jelly-RDF 1.0.x and
     * 1.1.x), as opposed to the column layout (Jelly-RDF 1.2.x).
     */
    public static boolean isRowLayout(int version) {
        return version < PROTO_VERSION_1_2_X;
    }

    /**
     * Whether a writer asked for this protocol version should write Jelly-RDF 1.0 or 1.1 (row
     * layout). Unlike {@link #isRowLayout}, which is for the version of an existing stream, version
     * 0 here means "not set", and the writer writes the newest version, Jelly-RDF 1.2.
     */
    public static boolean requestsRowLayout(int version) {
        return version == PROTO_VERSION_1_0_X || version == PROTO_VERSION_1_1_X;
    }
}
