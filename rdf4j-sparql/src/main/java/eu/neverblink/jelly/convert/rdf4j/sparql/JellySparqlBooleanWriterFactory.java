package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.io.OutputStream;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultFormat;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultWriter;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultWriterFactory;

/**
 * Factory for {@link JellySparqlBooleanWriter}, registered in RDF4J via META-INF/services.
 */
@ExperimentalApi
public final class JellySparqlBooleanWriterFactory implements BooleanQueryResultWriterFactory {

    @Override
    public BooleanQueryResultFormat getBooleanQueryResultFormat() {
        return JellySparqlFormat.JELLY_SPARQL_BOOLEAN;
    }

    @Override
    public BooleanQueryResultWriter getWriter(OutputStream out) {
        return new JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out);
    }
}
