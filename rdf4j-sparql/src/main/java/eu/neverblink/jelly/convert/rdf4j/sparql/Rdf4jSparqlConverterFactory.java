package eu.neverblink.jelly.convert.rdf4j.sparql;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jDatatype;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jDecoderConverter;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jEncoderConverter;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.sparql.JellySparqlConverterFactory;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

/**
 * A factory for creating Jelly-SPARQL encoders and decoders for RDF4J.
 */
@ExperimentalApi
public final class Rdf4jSparqlConverterFactory
    extends JellySparqlConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter>
{

    private static final Rdf4jSparqlConverterFactory INSTANCE = new Rdf4jSparqlConverterFactory(
        Rdf4jConverterFactory.getInstance()
    );

    private Rdf4jSparqlConverterFactory(
        JellyConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter> converterFactory
    ) {
        super(converterFactory);
    }

    /**
     * Returns the default singleton instance of the factory.
     * <p>
     * For decoding, this factory uses the {@link SimpleValueFactory} to create RDF4J values.
     *
     * @return the singleton instance
     */
    public static Rdf4jSparqlConverterFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a new instance of the factory with a custom {@link ValueFactory}.
     *
     * @param vf the {@link ValueFactory} to use for creating RDF4J values
     * @return a new instance of the factory
     */
    public static Rdf4jSparqlConverterFactory getInstance(ValueFactory vf) {
        return new Rdf4jSparqlConverterFactory(Rdf4jConverterFactory.getInstance(vf));
    }
}
