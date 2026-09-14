package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultParser;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultParserFactory;

/**
 * Factory for {@link JellySparqlTupleParser}, registered in RDF4J via META-INF/services.
 */
@ExperimentalApi
public final class JellySparqlTupleParserFactory implements TupleQueryResultParserFactory {

    @Override
    public TupleQueryResultFormat getTupleQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL;
    }

    @Override
    public TupleQueryResultParser getParser() {
        return new JellySparqlTupleParser();
    }
}
