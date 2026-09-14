package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultParser;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;

/**
 * RDF4J parser of boolean (ASK) results in the Jelly-SPARQL format.
 */
@ExperimentalApi
public final class JellySparqlBooleanParser extends AbstractJellySparqlParser implements BooleanQueryResultParser {

    public JellySparqlBooleanParser() {
        super();
    }

    public JellySparqlBooleanParser(ValueFactory valueFactory) {
        super(valueFactory);
    }

    @Override
    public BooleanQueryResultFormat getBooleanQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL_BOOLEAN;
    }

    @Override
    public BooleanQueryResultFormat getQueryResultFormat() {
        return getBooleanQueryResultFormat();
    }

    @Override
    public void parseQueryResult(InputStream in)
        throws IOException, QueryResultParseException, QueryResultHandlerException {
        parseInternal(in);
    }

    @Override
    @Deprecated
    public boolean parse(InputStream in) throws IOException, QueryResultParseException {
        return parseInternal(in);
    }
}
