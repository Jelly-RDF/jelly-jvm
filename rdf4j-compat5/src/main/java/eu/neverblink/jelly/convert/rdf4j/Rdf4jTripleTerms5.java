package eu.neverblink.jelly.convert.rdf4j;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;

/**
 * RDF4J 5.x implementation of {@link Rdf4jTripleTerms}, using the {@code Triple} type and
 * {@code ValueFactory.createTriple}. Compiled against RDF4J 5 (in the {@code rdf4j-compat5} module)
 * and only loaded/linked at runtime when running against RDF4J 5.x — where {@code Triple} exists.
 */
final class Rdf4jTripleTerms5 implements Rdf4jTripleTerms {

    @Override
    public boolean isTripleTerm(Value value) {
        return value instanceof Triple;
    }

    @Override
    public Value createTripleTerm(ValueFactory vf, Resource s, IRI p, Value o) {
        return vf.createTriple(s, p, o);
    }

    @Override
    public Resource getSubject(Value tripleTerm) {
        return ((Triple) tripleTerm).getSubject();
    }

    @Override
    public IRI getPredicate(Value tripleTerm) {
        return ((Triple) tripleTerm).getPredicate();
    }

    @Override
    public Value getObject(Value tripleTerm) {
        return ((Triple) tripleTerm).getObject();
    }
}
