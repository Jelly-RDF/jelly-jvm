package eu.neverblink.jelly.convert.neo4j.rio;

import eu.neverblink.jelly.convert.neo4j.JellyPlugin;
import eu.neverblink.jelly.convert.rdf4j.rio.JellyParserFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParserFactory;

public final class JellyBase64ParserFactory implements RDFParserFactory {
    static {
        // Leech off of RDF4J's registration mechanism and register our plugin in Neo4j.
        JellyPlugin.getInstance().initialize();
    }

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
