package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.utils.QuadMaker;
import eu.neverblink.jelly.core.utils.TripleMaker;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.base.CoreDatatype;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public final class Rdf4jDecoderConverter
    implements ProtoDecoderConverter<Value, Rdf4jDatatype>, TripleMaker<Value, Statement>, QuadMaker<Value, Statement> {

    private final ValueFactory vf;

    /**
     * Creates a new Rdf4jDecoderConverter.
     * <p>
     * This converter uses the {@link SimpleValueFactory} to create RDF4J values. You can override this by
     * used the one-parameter constructor that takes a {@link ValueFactory} instance.
     */
    public Rdf4jDecoderConverter() {
        vf = SimpleValueFactory.getInstance();
    }

    /**
     * Creates a new Rdf4jDecoderConverter with a custom ValueFactory.
     *
     * @param vf the ValueFactory to use for creating RDF4J values
     */
    public Rdf4jDecoderConverter(ValueFactory vf) {
        this.vf = vf;
    }

    @Override
    public Value makeSimpleLiteral(String lex) {
        return vf.createLiteral(lex);
    }

    @Override
    public Value makeLangLiteral(String lex, String lang) {
        return vf.createLiteral(lex, lang);
    }

    @Override
    public Value makeDtLiteral(String lex, Rdf4jDatatype dt) {
        return vf.createLiteral(lex, dt.dt(), dt.coreDatatype());
    }

    @Override
    public Rdf4jDatatype makeDatatype(String dt) {
        var iri = vf.createIRI(dt);
        final var coreDatatype = CoreDatatype.from(iri);
        if (coreDatatype != CoreDatatype.NONE) {
            // If it's a core datatype, use the core IRI to allow for reference equality checks.
            iri = coreDatatype.getIri();
        }
        return new Rdf4jDatatype(iri, coreDatatype);
    }

    @Override
    public Value makeBlankNode(String label) {
        return vf.createBNode(label);
    }

    @Override
    public Value makeIriNode(String iri) {
        return vf.createIRI(iri);
    }

    @Override
    public Value makeTripleNode(Value s, Value p, Value o) {
        try {
            // RDF4J doesn't accept generalized statements (unlike Jena) which is why we need to do a type cast here.
            return vf.createTriple((Resource) s, (IRI) p, o);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized triple node with %s, %s, %s".formatted(s, p, o),
                e
            );
        }
    }

    @Override
    public Value makeDefaultGraphNode() {
        return null;
    }

    @Override
    public Statement makeQuad(Value subject, Value predicate, Value object, Value graph) {
        try {
            return vf.createStatement((Resource) subject, (IRI) predicate, object, (Resource) graph);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized quad with %s, %s, %s, %s".formatted(subject, predicate, object, graph),
                e
            );
        }
    }

    @Override
    public Statement makeTriple(Value subject, Value predicate, Value object) {
        try {
            return vf.createStatement((Resource) subject, (IRI) predicate, object);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized triple with %s, %s, %s".formatted(subject, predicate, object),
                e
            );
        }
    }
}
