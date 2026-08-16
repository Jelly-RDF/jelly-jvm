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

    public static final int PROTO_VERSION_1_0_X = 1;
    public static final int PROTO_VERSION = PROTO_VERSION_1_0_X;

    public static final String PROTO_SEMANTIC_VERSION_1_0_0 = "1.0.0"; // First protocol version (proposal)
    public static final String PROTO_SEMANTIC_VERSION = PROTO_SEMANTIC_VERSION_1_0_0;
}
