package eu.neverblink.jelly.convert.jena.sparql;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.convert.jena.JenaDecoderConverter;
import eu.neverblink.jelly.convert.jena.JenaEncoderConverter;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.sparql.JellySparqlConverterFactory;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;

/**
 * A factory for creating Jelly-SPARQL encoders and decoders for Apache Jena.
 */
@ExperimentalApi
public final class JenaSparqlConverterFactory
    extends JellySparqlConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter>
{

    private static final JenaSparqlConverterFactory INSTANCE = new JenaSparqlConverterFactory(
        JenaConverterFactory.getInstance()
    );

    private JenaSparqlConverterFactory(
        JellyConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> converterFactory
    ) {
        super(converterFactory);
    }

    /**
     * Returns the singleton instance of the factory.
     *
     * @return the singleton instance
     */
    public static JenaSparqlConverterFactory getInstance() {
        return INSTANCE;
    }
}
