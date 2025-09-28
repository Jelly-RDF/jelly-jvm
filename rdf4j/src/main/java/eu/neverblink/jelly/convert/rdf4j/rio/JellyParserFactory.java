package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat.JELLY;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;

public final class JellyParserFactory implements RDFParserFactory {

    private boolean checking = false;

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY;
    }

    /**
     * Creates a new JellyParser instance that will either use RDF4J's full parsing stack or not,
     * depending on the value of {@link #isChecking()}. The default is false, so the parser will NOT
     * respect BasicParserSettings, as it does not use RDF4J's parsing stack. It will never validate
     * IRIs, language tags, or datatypes.
     * <p>
     * If {@link #isChecking()} is true, the parser does use RDF4J's full parsing stack,
     * including literal, datatype, IRI, and language tag validation.
     * <p>
     * This can be later overridden on the parser instance itself by changing the {@link JellyParserSettings#CHECKING}
     * setting.
     *
     * @return a new JellyParser instance
     */
    @Override
    public RDFParser getParser() {
        final var parser = new JellyParser();
        parser.set(JellyParserSettings.CHECKING, checking);
        return parser;
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

    /**
     * Set whether the parser created by {@link #getParser()} will use RDF4J's full parsing stack,
     * including literal, datatype, IRI, and language tag validation.
     * @param checking true to use RDF4J's checking stack, false to use a faster non-checking parser.
     * @return this
     */
    public JellyParserFactory setChecking(boolean checking) {
        this.checking = checking;
        return this;
    }

    /**
     * Whether the parser created by {@link #getParser()} will use RDF4J's full parsing stack,
     * including literal, datatype, IRI, and language tag validation.
     * @return true if the parser will use RDF4J's checking stack, false to use a faster non-checking parser.
     */
    public boolean isChecking() {
        return checking;
    }
}
