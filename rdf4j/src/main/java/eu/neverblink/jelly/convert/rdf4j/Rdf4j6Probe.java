package eu.neverblink.jelly.convert.rdf4j;

import org.eclipse.rdf4j.model.TripleTerm;

/**
 * Detects — without reflection — whether RDF4J 6+ is on the classpath.
 *
 * <p>Executing {@link #isPresent()} resolves the {@code TripleTerm} class literal. On RDF4J 6.x
 * this succeeds; on RDF4J 5.x, where {@code TripleTerm} does not exist, the class resolution throws
 * a {@link LinkageError} (specifically {@code NoClassDefFoundError}). This probe is isolated in its
 * own class so that {@link Rdf4jCompatHelper} itself never references an RDF4J-6-only type and thus
 * always links, on both RDF4J versions.
 */
final class Rdf4j6Probe {

    private Rdf4j6Probe() {}

    static boolean isPresent() {
        return TripleTerm.class != null;
    }
}
