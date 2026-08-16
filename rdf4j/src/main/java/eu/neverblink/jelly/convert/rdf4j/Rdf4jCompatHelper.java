package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.InternalApi;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;

/**
 * Bridges the incompatible RDF-star / RDF 1.2 triple-term APIs of RDF4J 5.x and 6.x.
 *
 * <ul>
 *   <li>RDF4J 5.x: {@code Triple} + {@code ValueFactory.createTriple}</li>
 *   <li>RDF4J 6.x: {@code TripleTerm} + {@code ValueFactory.createTripleTerm}</li>
 * </ul>
 *
 * <p>These are mutually exclusive — neither method/type exists in the other version — so a single
 * class cannot reference both. Instead the version-specific calls live in {@link Rdf4jTripleTerms5}
 * (compiled against RDF4J 5, in the {@code rdf4j-compat5} module) and {@link Rdf4jTripleTerms6}
 * (compiled against RDF4J 6, this module). We pick the right implementation once, based on whether
 * {@code org.eclipse.rdf4j.model.TripleTerm} resolves at runtime (see {@link Rdf4j6Probe}), using a
 * plain class-literal reference and a {@link LinkageError} catch. <strong>No reflection.</strong>
 * The implementation that does not match the runtime RDF4J version is never instantiated, so its
 * references to the absent RDF4J types are never linked.
 */
@InternalApi
final class Rdf4jCompatHelper {

    private static final Rdf4jTripleTerms IMPL = create();

    private static Rdf4jTripleTerms create() {
        if (isRdf4j6()) {
            return new Rdf4jTripleTerms6();
        } else {
            return new Rdf4jTripleTerms5();
        }
    }

    private static boolean isRdf4j6() {
        try {
            return Rdf4j6Probe.isPresent();
        } catch (LinkageError e) {
            // org.eclipse.rdf4j.model.TripleTerm is absent -> we are running against RDF4J 5.x.
            return false;
        }
    }

    private Rdf4jCompatHelper() {}

    static boolean isTripleTerm(Value value) {
        return IMPL.isTripleTerm(value);
    }

    static Value createTripleTerm(ValueFactory vf, Resource s, IRI p, Value o) {
        return IMPL.createTripleTerm(vf, s, p, o);
    }

    static Resource getSubject(Value tripleTerm) {
        return IMPL.getSubject(tripleTerm);
    }

    static IRI getPredicate(Value tripleTerm) {
        return IMPL.getPredicate(tripleTerm);
    }

    static Value getObject(Value tripleTerm) {
        return IMPL.getObject(tripleTerm);
    }
}
