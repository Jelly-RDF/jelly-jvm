package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.JellyConverterFactory;
import org.eclipse.rdf4j.model.Value;

public final class Rdf4jConverterFactory
    extends JellyConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter> {

    @Override
    public Rdf4jEncoderConverter encoderConverter() {
        return new Rdf4jEncoderConverter();
    }

    @Override
    public Rdf4jDecoderConverter decoderConverter() {
        return new Rdf4jDecoderConverter();
    }
}
