package eu.neverblink.jelly.convert.rdf4j;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.TripleTerm;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;

/**
 * RDF4J 6.x implementation of {@link Rdf4jTripleTerms}, using the {@code TripleTerm} type and
 * {@code ValueFactory.createTripleTerm}. Compiled against RDF4J 6 (this module) and only
 * loaded/linked at runtime when running against RDF4J 6.x — where {@code TripleTerm} exists.
 */
final class Rdf4jTripleTerms6 implements Rdf4jTripleTerms {

    @Override
    public boolean isTripleTerm(Value value) {
        return value instanceof TripleTerm;
    }

    @Override
    public Value createTripleTerm(ValueFactory vf, Resource s, IRI p, Value o) {
        return vf.createTripleTerm(s, p, o);
    }

    @Override
    public Resource getSubject(Value tripleTerm) {
        return ((TripleTerm) tripleTerm).getSubject();
    }

    @Override
    public IRI getPredicate(Value tripleTerm) {
        return ((TripleTerm) tripleTerm).getPredicate();
    }

    @Override
    public Value getObject(Value tripleTerm) {
        return ((TripleTerm) tripleTerm).getObject();
    }
}
