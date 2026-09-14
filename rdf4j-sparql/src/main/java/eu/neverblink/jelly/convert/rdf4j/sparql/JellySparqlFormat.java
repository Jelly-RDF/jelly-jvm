package eu.neverblink.jelly.convert.rdf4j.sparql;

import static eu.neverblink.jelly.core.sparql.JellySparqlConstants.*;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.nio.charset.Charset;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;

/**
 * Definition of the Jelly-SPARQL query result format for RDF4J.
 * <p>
 * RDF4J keeps solution sequences and boolean (ASK) results in separate registries, so the same
 * format has to be declared twice.
 */
@ExperimentalApi
public final class JellySparqlFormat {

    private JellySparqlFormat() {}

    /**
     * Jelly-SPARQL as a format for solution sequences (SELECT results).
     */
    public static final TupleQueryResultFormat JELLY_SPARQL = new TupleQueryResultFormat(
        JELLY_SPARQL_NAME,
        JELLY_SPARQL_CONTENT_TYPE,
        (Charset) null,
        JELLY_SPARQL_FILE_EXTENSION,
        // Jelly-SPARQL has no native encoding for RDF 1.2 triple terms. Saying so lets RDF4J apply
        // its IRI-encoding fallback for callers that ask for it.
        // TODO: implement RDF 1.2
        false
    );

    /**
     * Jelly-SPARQL as a format for boolean (ASK) results.
     */
    public static final BooleanQueryResultFormat JELLY_SPARQL_BOOLEAN = new BooleanQueryResultFormat(
        JELLY_SPARQL_NAME,
        JELLY_SPARQL_CONTENT_TYPE,
        (Charset) null,
        JELLY_SPARQL_FILE_EXTENSION
    );
}
