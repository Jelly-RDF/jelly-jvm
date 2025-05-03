package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.WriterGraphRIOT;
import org.apache.jena.riot.WriterGraphRIOTFactory;

/**
 * A factory for creating a Jelly writer for a graph.
 */
public final class JellyGraphWriterFactory implements WriterGraphRIOTFactory {

    @Override
    public WriterGraphRIOT create(RDFFormat syntaxForm) {
        final var converterFactory = new JenaConverterFactory();
        return new JellyGraphWriter(converterFactory, JellyFormatVariant.getVariant(syntaxForm));
    }
}
