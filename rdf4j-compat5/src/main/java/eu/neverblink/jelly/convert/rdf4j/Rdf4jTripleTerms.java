package eu.neverblink.jelly.convert.rdf4j;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;

/**
 * Abstraction over the RDF-star / RDF 1.2 "triple term" API, which is incompatible between
 * RDF4J 5.x ({@code Triple} / {@code ValueFactory.createTriple}) and RDF4J 6.x
 * ({@code TripleTerm} / {@code ValueFactory.createTripleTerm}).
 *
 * <p>The two implementations ({@link Rdf4jTripleTerms5}, {@code Rdf4jTripleTerms6}) reference only
 * their own RDF4J version's types, so each is compiled against the matching RDF4J version and only
 * loaded at runtime when that version is present. No reflection is involved. Method signatures here
 * use only types that exist in both RDF4J versions, so this interface compiles against either.
 */
interface Rdf4jTripleTerms {
    boolean isTripleTerm(Value value);

    Value createTripleTerm(ValueFactory vf, Resource s, IRI p, Value o);

    Resource getSubject(Value tripleTerm);

    IRI getPredicate(Value tripleTerm);

    Value getObject(Value tripleTerm);
}
