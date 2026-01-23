package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.JellyConverterFactory;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

/**
 * A singleton factory for creating Jelly RDF4J converters.
 * <p>
 * This class is a singleton and should be accessed via the {@link #getInstance()} method.
 */
public final class Rdf4jConverterFactory
    extends JellyConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter>
{

    private static final Rdf4jConverterFactory INSTANCE = new Rdf4jConverterFactory();

    private final Rdf4jEncoderConverter encoderConverter;
    private final Rdf4jDecoderConverter decoderConverter;

    private Rdf4jConverterFactory() {
        this(SimpleValueFactory.getInstance());
    }

    private Rdf4jConverterFactory(ValueFactory valueFactory) {
        this.encoderConverter = new Rdf4jEncoderConverter();
        this.decoderConverter = new Rdf4jDecoderConverter(valueFactory);
    }

    /**
     * Returns the singleton instance of the {@link Rdf4jConverterFactory}.
     * <p>
     * For decoding, this factory uses the {@link SimpleValueFactory} to create RDF4J values.
     *
     * @return the singleton instance of the {@link Rdf4jConverterFactory}
     */
    public static Rdf4jConverterFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a new instance of the {@link Rdf4jConverterFactory} with a custom {@link ValueFactory}.
     *
     * @param vf the {@link ValueFactory} to use for creating RDF4J values
     * @return a new instance of the {@link Rdf4jConverterFactory}
     */
    public static Rdf4jConverterFactory getInstance(ValueFactory vf) {
        return new Rdf4jConverterFactory(vf);
    }

    @Override
    public Rdf4jEncoderConverter encoderConverter() {
        return encoderConverter;
    }

    @Override
    public Rdf4jDecoderConverter decoderConverter() {
        return decoderConverter;
    }
}
