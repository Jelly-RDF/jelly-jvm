package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;

public final class JellyParserFactory implements RDFParserFactory {

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    /**
     * Creates a new JellyParser instance that uses RDF4J's full parsing stack, including literal,
     * datatype, IRI, and language tag validation.
     * <p>
     * If you want to use a slightly faster non-checking parser, use the {@link #getParser(Rdf4jConverterFactory)}
     * method and pass in {@link Rdf4jConverterFactory#getInstance()}.
     *
     * @return a new JellyParser instance
     */
    @Override
    public RDFParser getParser() {
        return new JellyParser();
    }

    /**
     * Creates a new JellyParser instance with the specified converter factory. This parser will NOT
     * respect BasicParserSettings, as it does not use RDF4J's parsing stack. It will never validate
     * IRIs, language tags, or datatypes.
     * <p>
     * If you want to use RDF4J's full parsing stack, use the parameterless {@link #getParser()} method.
     *
     * @param converterFactory The converter factory to use for creating RDF4J values.
     * @return a new JellyParser instance
     */
    public RDFParser getParser(Rdf4jConverterFactory converterFactory) {
        return new JellyParser(converterFactory);
    }
}
