package eu.neverblink.jelly.convert.neo4j;

import eu.neverblink.jelly.convert.neo4j.rio.JellyBase64Format;
import n10s.rdf.aggregate.CollectTriples;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.UserAggregationFunction;

public final class JellyTripleCollector {

    static {
        // Call the initializer of the plugin to ensure any setup is done
        JellyPlugin.getInstance().initialize();
    }

    @UserAggregationFunction(name = "n10s.rdf.collect.jelly_base64")
    @Description(
        "n10s.rdf.collect(subject,predicate,object,isLiteral,literalType,literalLang) - " +
            "collects a set of triples as returned by n10s.rdf.export.* or n10s.rdf.stream.* " +
            "and returns them serialised as Jelly, encoded as base64."
    )
    public CollectTriples.TripleCollector collectJellyBase64() {
        return new CollectTriples.TripleCollector(JellyBase64Format.JELLY_BASE64);
    }
}
