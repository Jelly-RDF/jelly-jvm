package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.JellyConverterFactory;
import org.eclipse.rdf4j.model.Value;

/**
 * A singleton factory for creating Jelly RDF4J converters.
 * <p>
 * This class is a singleton and should be accessed via the {@link #getInstance()} method.
 */
public final class Rdf4jConverterFactory
    extends JellyConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter> {

    private static final Rdf4jConverterFactory INSTANCE = new Rdf4jConverterFactory();

    private Rdf4jConverterFactory() {}

    /**
     * Returns the singleton instance of the {@link Rdf4jConverterFactory}.
     *
     * @return the singleton instance of the {@link Rdf4jConverterFactory}
     */
    public static Rdf4jConverterFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public Rdf4jEncoderConverter encoderConverter() {
        return new Rdf4jEncoderConverter();
    }

    @Override
    public Rdf4jDecoderConverter decoderConverter() {
        return new Rdf4jDecoderConverter();
    }
}
