package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.JellyConverterFactory;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;

public final class JenaConverterFactory
    extends JellyConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> {

    @Override
    public JenaEncoderConverter encoderConverter() {
        return new JenaEncoderConverter();
    }

    @Override
    public JenaDecoderConverter decoderConverter() {
        return new JenaDecoderConverter();
    }
}
