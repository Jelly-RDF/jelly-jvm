package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.io.IOException;
import java.io.OutputStream;
import org.eclipse.rdf4j.query.QueryResultHandlerException;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultWriter;

/**
 * RDF4J writer of boolean (ASK) results in the Jelly-SPARQL format.
 */
@ExperimentalApi
public final class JellySparqlBooleanWriter extends AbstractJellySparqlWriter implements BooleanQueryResultWriter {

    public JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory converterFactory, OutputStream out) {
        super(converterFactory, out);
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
    public void write(boolean value) throws IOException {
        try {
            handleBoolean(value);
        } catch (QueryResultHandlerException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(e);
        }
    }
}
