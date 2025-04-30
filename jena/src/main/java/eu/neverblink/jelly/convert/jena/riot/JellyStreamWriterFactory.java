package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import java.io.OutputStream;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFWriterFactory;
import org.apache.jena.sparql.util.Context;

/**
 * A factory for creating a Jelly stream writer.
 */
public final class JellyStreamWriterFactory implements StreamRDFWriterFactory {

    @Override
    public StreamRDF create(OutputStream output, RDFFormat format, Context context) {
        final var converterFactory = new JenaConverterFactory();
        return new JellyStreamWriterAutodetectType(converterFactory, JellyUtils.getVariant(format), output);
    }
}
