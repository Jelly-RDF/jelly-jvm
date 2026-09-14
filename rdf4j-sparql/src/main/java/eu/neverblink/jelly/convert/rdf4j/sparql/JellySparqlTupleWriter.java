package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.io.OutputStream;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriter;

/**
 * RDF4J writer of solution sequences (SELECT results) in the Jelly-SPARQL format.
 */
@ExperimentalApi
public final class JellySparqlTupleWriter extends AbstractJellySparqlWriter implements TupleQueryResultWriter {

    public JellySparqlTupleWriter(Rdf4jSparqlConverterFactory converterFactory, OutputStream out) {
        super(converterFactory, out);
    }

    @Override
    public TupleQueryResultFormat getTupleQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL;
    }

    @Override
    public TupleQueryResultFormat getQueryResultFormat() {
        return getTupleQueryResultFormat();
    }
}
