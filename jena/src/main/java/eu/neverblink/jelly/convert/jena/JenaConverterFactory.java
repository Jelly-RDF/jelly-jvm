package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.JellyConverterFactory;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;

/**
 * A factory for creating Jena-specific encoder and decoder converters.
 * <p>
 * This class is a singleton and should be accessed via the {@link #getInstance()} method.
 */
public final class JenaConverterFactory
    extends JellyConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> {

    private static final JenaConverterFactory INSTANCE = new JenaConverterFactory();

    private JenaConverterFactory() {}

    /**
     * Returns the singleton instance of the {@link JenaConverterFactory}.
     *
     * @return the singleton instance of {@link JenaConverterFactory}
     */
    public static JenaConverterFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public JenaEncoderConverter encoderConverter() {
        return new JenaEncoderConverter();
    }

    @Override
    public JenaDecoderConverter decoderConverter() {
        return new JenaDecoderConverter();
    }
}
