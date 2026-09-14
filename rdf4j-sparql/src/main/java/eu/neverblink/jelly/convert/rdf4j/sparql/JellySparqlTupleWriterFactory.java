package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.io.OutputStream;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriter;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterFactory;

/**
 * Factory for {@link JellySparqlTupleWriter}, registered in RDF4J via META-INF/services.
 */
@ExperimentalApi
public final class JellySparqlTupleWriterFactory implements TupleQueryResultWriterFactory {

    @Override
    public TupleQueryResultFormat getTupleQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL;
    }

    @Override
    public TupleQueryResultWriter getWriter(OutputStream out) {
        return new JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out);
    }
}
