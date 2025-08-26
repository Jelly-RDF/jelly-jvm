package eu.neverblink.jelly.convert.neo4j.rio;

import eu.neverblink.jelly.convert.rdf4j.rio.JellyParserFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParserFactory;

public final class JellyBase64ParserFactory implements RDFParserFactory {

    private final JellyParserFactory innerFactory = new JellyParserFactory();

    @Override
    public RDFFormat getRDFFormat() {
        return JellyBase64Format.JELLY_BASE64;
    }

    @Override
    public JellyBase64Parser getParser() {
        return new JellyBase64Parser(innerFactory.getParser());
    }
}
