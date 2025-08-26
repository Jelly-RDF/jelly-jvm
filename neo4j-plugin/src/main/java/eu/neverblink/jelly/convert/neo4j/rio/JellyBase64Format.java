package eu.neverblink.jelly.convert.neo4j.rio;

import org.eclipse.rdf4j.rio.RDFFormat;

/**
 * Base64-encoded Jelly format variant.
 * This is used in Neo4j procedures to return Jelly data as a string, because Neo4j doesn't
 * support binary data types.
 */
public final class JellyBase64Format {

    private JellyBase64Format() {}

    public static final RDFFormat JELLY_BASE64 = new RDFFormat(
        "Jelly-base64",
        "application/x-jelly-rdf-base64",
        null,
        "jelly_b64",
        true,
        true,
        true
    );
}
