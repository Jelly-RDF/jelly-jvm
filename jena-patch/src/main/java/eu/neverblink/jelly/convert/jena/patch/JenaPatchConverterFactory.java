package eu.neverblink.jelly.convert.jena.patch;

import eu.neverblink.jelly.convert.jena.JenaDecoderConverter;
import eu.neverblink.jelly.convert.jena.JenaEncoderConverter;
import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.patch.JellyPatchConverterFactory;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;

/**
 * Factory for Jena-based Jelly-Patch encoders and decoders.
 */
@ExperimentalApi
public final class JenaPatchConverterFactory
    extends JellyPatchConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> {

    public JenaPatchConverterFactory(
        JellyConverterFactory<Node, RDFDatatype, JenaEncoderConverter, JenaDecoderConverter> converterFactory
    ) {
        super(converterFactory);
    }
}
