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
        final var converterFactory = Rdf4jConverterFactory.getInstance();
        return new JellyParser(converterFactory);
    }
}
