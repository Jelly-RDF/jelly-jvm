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

    @Override
    public RDFParser getParser() {
        return getParser(Rdf4jConverterFactory.getInstance());
    }

    /**
     * Creates a new JellyParser instance with the specified converter factory.
     * @param converterFactory
     * The converter factory to use for creating RDF4J values.
     * @return a new JellyParser instance
     */
    public RDFParser getParser(Rdf4jConverterFactory converterFactory) {
        return new JellyParser(converterFactory);
    }
}
