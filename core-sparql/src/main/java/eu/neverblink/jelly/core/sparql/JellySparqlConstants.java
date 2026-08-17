package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;

/**
 * Constants for the Jelly-SPARQL extension.
 */
@ExperimentalApi
public final class JellySparqlConstants {

    private JellySparqlConstants() {}

    public static final String JELLY_SPARQL_NAME = "Jelly-SPARQL";
    public static final String JELLY_SPARQL_FILE_EXTENSION = "jellys";
    public static final String JELLY_SPARQL_CONTENT_TYPE = "application/x-jelly-sparql";

    /**
     * How many values (cells) a writer puts in one frame by default.
     * <p>
     * Frames are sized in values rather than rows because the cost of a row depends on how many
     * variables the result set has: 4096 values is 4096 rows of a single-variable result set, but
     * only 204 rows of a 20-variable one. Writers turn this into a row limit once, when the number
     * of variables becomes known.
     */
    public static final int DEFAULT_MAX_VALUES_PER_FRAME = 4096;

    public static final int PROTO_VERSION_1_0_X = 1;
    public static final int PROTO_VERSION = PROTO_VERSION_1_0_X;

    public static final String PROTO_SEMANTIC_VERSION_1_0_0 = "1.0.0"; // First protocol version (proposal)
    public static final String PROTO_SEMANTIC_VERSION = PROTO_SEMANTIC_VERSION_1_0_0;
}
