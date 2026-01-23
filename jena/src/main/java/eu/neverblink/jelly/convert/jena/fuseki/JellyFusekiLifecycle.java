package eu.neverblink.jelly.convert.jena.fuseki;

import static eu.neverblink.jelly.core.JellyConstants.JELLY_CONTENT_TYPE;

import java.util.ArrayList;
import java.util.Optional;
import org.apache.jena.atlas.web.AcceptList;
import org.apache.jena.atlas.web.MediaRange;
import org.apache.jena.fuseki.DEF;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.sys.JenaSubsystemLifecycle;

/**
 * A Jena module that adds Jelly content type to the list of accepted content types in Fuseki.
 * This isn't a Fuseki module, because Fuseki modules are not supported in all distributions of Fuseki, see:
 * <a href="https://github.com/apache/jena/issues/2774">Issue 2774</a>
 * <p>
 * This allows users to use the Accept header set to application/x-jelly-rdf to request Jelly RDF responses.
 * It works for SPARQL CONSTRUCT queries and for the Graph Store Protocol.
 */
public final class JellyFusekiLifecycle implements JenaSubsystemLifecycle {

    public static final MediaRange JELLY_MEDIA_RANGE = new MediaRange(JELLY_CONTENT_TYPE);

    @Override
    public void start() {
        try {
            // Add Jelly content type to the list of accepted content types
            maybeAddJellyToList(DEF.constructOffer).ifPresent(offer -> DEF.constructOffer = offer);
            maybeAddJellyToList(DEF.rdfOffer).ifPresent(offer -> DEF.rdfOffer = offer);
            maybeAddJellyToList(DEF.quadsOffer).ifPresent(offer -> {
                DEF.quadsOffer = offer;
                Fuseki.serverLog.info("Jelly: Added {} to the list of accepted content types", JELLY_CONTENT_TYPE);
            });
        } catch (NoClassDefFoundError e) {
            // ignore, we are not running Fuseki
        } catch (IllegalAccessError e) {
            Fuseki.serverLog.warn(
                "Jelly: Cannot register the {} content type, because you are running an " +
                    "Apache Jena Fuseki version that doesn't support content type registration. " +
                    "Update to Fuseki 5.2.0 or newer for this to work.",
                JELLY_CONTENT_TYPE
            );
        }
    }

    @Override
    public void stop() {
        // No-op
    }

    @Override
    public int level() {
        // Initialize after JellySubsystemLifecycle
        return 502;
    }

    /**
     * Adds the Jelly content type to the list of accepted content types if it is not already present.
     * @param list current list of accepted content types
     * @return none or a new list with Jelly content type
     */
    private static Optional<AcceptList> maybeAddJellyToList(AcceptList list) {
        if (list.entries().contains(JELLY_MEDIA_RANGE)) {
            return Optional.empty();
        }

        final var newList = new ArrayList<>(list.entries());
        newList.add(JELLY_MEDIA_RANGE);
        return Optional.of(new AcceptList(newList));
    }
}
