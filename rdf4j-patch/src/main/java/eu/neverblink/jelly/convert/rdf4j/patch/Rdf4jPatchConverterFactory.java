package eu.neverblink.jelly.convert.rdf4j.patch;

import com.google.protobuf.ExperimentalApi;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jDatatype;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jDecoderConverter;
import eu.neverblink.jelly.convert.rdf4j.Rdf4jEncoderConverter;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.patch.JellyPatchConverterFactory;
import org.eclipse.rdf4j.model.Value;

@ExperimentalApi
public final class Rdf4jPatchConverterFactory
    extends JellyPatchConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter> {

    public Rdf4jPatchConverterFactory(
        JellyConverterFactory<Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter> converterFactory
    ) {
        super(converterFactory);
    }
}
