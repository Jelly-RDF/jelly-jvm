package eu.neverblink.jelly.convert.rdf4j.rio;

/**
 * Service interface for changing the defaults of the parsers made by {@link JellyParserFactory#getParser()}
 * and {@link JellyParser#JellyParser()}.
 * <p>
 * Implementations are found with {@link java.util.ServiceLoader}, so to use one, list its class name in
 * <code>META-INF/services/eu.neverblink.jelly.convert.rdf4j.rio.JellyParserDefaults</code>.
 * <p>
 * This is meant for libraries that bundle Jelly and want it to behave like their other parsers.
 * For example, RDF4J's own Jelly module uses it to turn on checking by default, so that RDF4J's
 * validation settings are respected, like in RDF4J's other parsers.
 */
public interface JellyParserDefaults {
    /**
     * Whether the parsers should use RDF4J's checking stack when {@link JellyParserSettings#CHECKING}
     * is not set. If more than one implementation is found, checking is on if any of them returns true.
     *
     * @return true to use RDF4J's checking stack by default
     */
    boolean checking();
}
