package eu.neverblink.jelly.convert.neo4j;

import eu.neverblink.jelly.convert.neo4j.rio.JellyBase64Format;
import eu.neverblink.jelly.convert.rdf4j.rio.JellyFormat;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.rdf4j.rio.RDFFormat;

public class JellyPlugin {

    private static final JellyPlugin INSTANCE = new JellyPlugin();

    public static JellyPlugin getInstance() {
        return INSTANCE;
    }

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Registers the Jelly RDF format with Neosemantics.
     */
    public void initialize() {
        if (initialized.get()) {
            return; // Already initialized
        }
        synchronized (this) {
            initialized.set(true); // Set to true early to silence concurrent attempts
            try {
                registerParser();
                registerRdfEndpoint();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to register the Jelly format in Neosemantics", e);
            }
        }
    }

    private void registerParser() throws NoSuchFieldException, IllegalAccessException {
        final var parsersField = n10s.CommonProcedures.class.getDeclaredField("availableParsers");
        parsersField.setAccessible(true);
        final var currentParsers = (RDFFormat[]) parsersField.get(null);
        parsersField.set(null, addJellyToArray(currentParsers));
    }

    private void registerRdfEndpoint() throws NoSuchFieldException, IllegalAccessException {
        final var parsersField = n10s.endpoint.RDFEndpoint.class.getDeclaredField("availableParsers");
        parsersField.setAccessible(true);
        final var currentParsers = (RDFFormat[]) parsersField.get(null);
        parsersField.set(null, addJellyToArray(currentParsers));
    }

    private RDFFormat[] addJellyToArray(RDFFormat[] array) {
        if (Arrays.asList(array).contains(JellyFormat.JELLY)) {
            return array; // Already present
        }
        RDFFormat[] newArray = Arrays.copyOf(array, array.length + 2);
        newArray[newArray.length - 2] = JellyFormat.JELLY;
        newArray[newArray.length - 1] = JellyBase64Format.JELLY_BASE64;
        return newArray;
    }
}
