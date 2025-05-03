package eu.neverblink.jelly.convert.jena.patch;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import eu.neverblink.jelly.convert.jena.JenaDecoderConverter;
import eu.neverblink.jelly.convert.jena.JenaEncoderConverter;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.patch.JellyPatchConverterFactory;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;

/**
 * Factory for Jena-based Jelly-Patch encoders and decoders.
 * <p>
 * This class is a singleton and should be accessed via the {@link #getInstance()} method.
 */
@ExperimentalApi
public final class JenaPatchConverterFactory
    extends JellyPatchConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> {

    private static final JenaPatchConverterFactory INSTANCE = new JenaPatchConverterFactory(
        JenaConverterFactory.getInstance()
    );

    private JenaPatchConverterFactory(
        JellyConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> converterFactory
    ) {
        super(converterFactory);
    }

    /**
     * Returns the singleton instance of the {@link JenaPatchConverterFactory}.
     *
     * @return the singleton instance of the {@link JenaPatchConverterFactory}
     */
    public static JenaPatchConverterFactory getInstance() {
        return INSTANCE;
    }
}
