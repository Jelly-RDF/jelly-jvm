package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.core.JellyConstants.*;

import org.eclipse.rdf4j.rio.RDFFormat;

public final class JellyConstants {

    private JellyConstants() {}

    public static RDFFormat JELLY_RDF_FORMAT = new RDFFormat(
        JELLY_NAME,
        JELLY_CONTENT_TYPE,
        null,
        JELLY_FILE_EXTENSION,
        true, // supports namespaces if ENABLE_NAMESPACE_DECLARATIONS is true, otherwise ignored
        true,
        true
    );
}
