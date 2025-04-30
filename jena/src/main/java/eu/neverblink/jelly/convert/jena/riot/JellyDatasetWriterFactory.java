package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.convert.jena.JenaConverterFactory;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.WriterDatasetRIOT;
import org.apache.jena.riot.WriterDatasetRIOTFactory;

/**
 * A factory for creating a Jelly writer for a dataset.
 */
public final class JellyDatasetWriterFactory implements WriterDatasetRIOTFactory {

    @Override
    public WriterDatasetRIOT create(RDFFormat syntaxForm) {
        final var converterFactory = new JenaConverterFactory();
        return new JellyDatasetWriter(converterFactory, JellyUtils.getVariant(syntaxForm));
    }
}
