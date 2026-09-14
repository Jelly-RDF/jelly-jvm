package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.io.IOException;
import java.io.InputStream;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultParser;

/**
 * RDF4J parser of solution sequences (SELECT results) in the Jelly-SPARQL format.
 */
@ExperimentalApi
public final class JellySparqlTupleParser extends AbstractJellySparqlParser implements TupleQueryResultParser {

    public JellySparqlTupleParser() {
        super();
    }

    public JellySparqlTupleParser(ValueFactory valueFactory) {
        super(valueFactory);
    }

    @Override
    public TupleQueryResultFormat getTupleQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL;
    }

    @Override
    public TupleQueryResultFormat getQueryResultFormat() {
        return getTupleQueryResultFormat();
    }

    @Override
    public void setTupleQueryResultHandler(TupleQueryResultHandler handler) {
        setQueryResultHandler(handler);
    }

    @Override
    public void parseQueryResult(InputStream in)
        throws IOException, QueryResultParseException, QueryResultHandlerException {
        parseInternal(in);
    }

    @Override
    @Deprecated
    public void parse(InputStream in) throws IOException, QueryResultParseException, QueryResultHandlerException {
        parseQueryResult(in);
    }
}
