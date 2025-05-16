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

    private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

    @Override
    public Value makeSimpleLiteral(String lex) {
        return VALUE_FACTORY.createLiteral(lex);
    }

    @Override
    public Value makeLangLiteral(String lex, String lang) {
        return VALUE_FACTORY.createLiteral(lex, lang);
    }

    @Override
    public Value makeDtLiteral(String lex, Rdf4jDatatype dt) {
        return VALUE_FACTORY.createLiteral(lex, dt.dt(), dt.coreDatatype());
    }

    @Override
    public Rdf4jDatatype makeDatatype(String dt) {
        final var iri = VALUE_FACTORY.createIRI(dt);
        return new Rdf4jDatatype(iri, CoreDatatype.from(iri));
    }

    @Override
    public Value makeBlankNode(String label) {
        return VALUE_FACTORY.createBNode(label);
    }

    @Override
    public Value makeIriNode(String iri) {
        return VALUE_FACTORY.createIRI(iri);
    }

    @Override
    public Value makeTripleNode(Value s, Value p, Value o) {
        try {
            // RDF4J doesn't accept generalized statements (unlike Jena) which is why we need to do a type cast here.
            return VALUE_FACTORY.createTriple((Resource) s, (IRI) p, o);
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
            return VALUE_FACTORY.createStatement((Resource) subject, (IRI) predicate, object, (Resource) graph);
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
            return VALUE_FACTORY.createStatement((Resource) subject, (IRI) predicate, object);
        } catch (ClassCastException e) {
            throw new RdfProtoDeserializationError(
                "Cannot create generalized triple with %s, %s, %s".formatted(subject, predicate, object),
                e
            );
        }
    }
}
