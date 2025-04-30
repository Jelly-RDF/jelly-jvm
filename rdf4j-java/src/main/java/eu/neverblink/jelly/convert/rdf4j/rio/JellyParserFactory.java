package eu.neverblink.jelly.convert.rdf4j.rio;

import static eu.neverblink.jelly.convert.rdf4j.rio.JellyConstants.JELLY_RDF_FORMAT;

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFParserFactory;

public final class JellyParserFactory implements RDFParserFactory {

    @Override
    public RDFFormat getRDFFormat() {
        return JELLY_RDF_FORMAT;
    }

    @Override
    public RDFParser getParser() {
        final var converterFactory = new Rdf4jConverterFactory();
        return new JellyParser(converterFactory);
    }
}
