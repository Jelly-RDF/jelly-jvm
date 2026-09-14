package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultParser;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultParserFactory;

/**
 * Factory for {@link JellySparqlBooleanParser}, registered in RDF4J via META-INF/services.
 */
@ExperimentalApi
public final class JellySparqlBooleanParserFactory implements BooleanQueryResultParserFactory {

    @Override
    public BooleanQueryResultFormat getBooleanQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL_BOOLEAN;
    }

    @Override
    public BooleanQueryResultParser getParser() {
        return new JellySparqlBooleanParser();
    }
}
