package eu.neverblink.jelly.convert.jena.sparql;

import static eu.neverblink.jelly.core.sparql.JellySparqlConstants.JELLY_SPARQL_CONTENT_TYPE;

import eu.neverblink.jelly.core.ExperimentalApi;
import java.util.ArrayList;
import java.util.Optional;
import org.apache.jena.atlas.web.AcceptList;
import org.apache.jena.atlas.web.MediaRange;
import org.apache.jena.fuseki.DEF;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.sys.JenaSubsystemLifecycle;

/**
 * A Jena module that adds the Jelly-SPARQL content type to the lists of result set content types
 * that Fuseki offers. This isn't a Fuseki module, because Fuseki modules are not supported in all
 * distributions of Fuseki, see: <a href="https://github.com/apache/jena/issues/2774">Issue 2774</a>
 * <p>
 * This allows users to use the Accept header set to application/x-jelly-sparql to request
 * Jelly-SPARQL responses. It works for SPARQL SELECT and ASK queries.
 */
@ExperimentalApi
public final class JellySparqlFusekiLifecycle implements JenaSubsystemLifecycle {

    public static final MediaRange JELLY_SPARQL_MEDIA_RANGE = new MediaRange(JELLY_SPARQL_CONTENT_TYPE);

    @Override
    public void start() {
        try {
            // rsOfferTable is used for SELECT queries, rsOfferBoolean for ASK queries
            maybeAddJellyToList(DEF.rsOfferTable).ifPresent(offer -> DEF.rsOfferTable = offer);
            maybeAddJellyToList(DEF.rsOfferBoolean).ifPresent(offer -> {
                DEF.rsOfferBoolean = offer;
                Fuseki.serverLog.info(
                    "Jelly: Added {} to the list of accepted result set content types",
                    JELLY_SPARQL_CONTENT_TYPE
                );
            });
        } catch (NoClassDefFoundError e) {
            // ignore, we are not running Fuseki
        } catch (IllegalAccessError e) {
            Fuseki.serverLog.warn(
                "Jelly: Cannot register the {} content type, because you are running an " +
                    "Apache Jena Fuseki version that doesn't support content type registration.",
                JELLY_SPARQL_CONTENT_TYPE
            );
        }
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public int level() {
        // Initialize after JellySparqlSubsystemLifecycle (501), which registers the language, and
        // after JellyFusekiLifecycle (502), which does the same job for Jelly RDF.
        return 503;
    }

    /**
     * Adds the Jelly-SPARQL content type to the list of accepted content types if it is not already
     * present.
     * @param list current list of accepted content types
     * @return none or a new list with the Jelly-SPARQL content type
     */
    private static Optional<AcceptList> maybeAddJellyToList(AcceptList list) {
        if (list.entries().contains(JELLY_SPARQL_MEDIA_RANGE)) {
            return Optional.empty();
        }

        final var newList = new ArrayList<>(list.entries());
        newList.add(JELLY_SPARQL_MEDIA_RANGE);
        return Optional.of(new AcceptList(newList));
    }
}
