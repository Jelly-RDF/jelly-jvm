package eu.neverblink.jelly.convert.rdf4j.patch;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jDatatype;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jDecoderConverter;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jEncoderConverter;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.patch.JellyPatchConverterFactory;
import org.eclipse.rdf4j.model.Value;

/**
 * A factory for creating RDF4J patch converters.
 * <p>
 * This class is a singleton and should be accessed via the {@link #getInstance()} method.
 */
@ExperimentalApi
public final class Rdf4jPatchConverterFactory
    extends JellyPatchConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter>
{

    private static final Rdf4jPatchConverterFactory INSTANCE = new Rdf4jPatchConverterFactory(
        Rdf4jConverterFactory.getInstance()
    );

    private Rdf4jPatchConverterFactory(
        JellyConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter> converterFactory
    ) {
        super(converterFactory);
    }

    /**
     * Returns the singleton instance of the {@link Rdf4jPatchConverterFactory}.
     *
     * @return the singleton instance of the {@link Rdf4jPatchConverterFactory}
     */
    public static Rdf4jPatchConverterFactory getInstance() {
        return INSTANCE;
    }
}
