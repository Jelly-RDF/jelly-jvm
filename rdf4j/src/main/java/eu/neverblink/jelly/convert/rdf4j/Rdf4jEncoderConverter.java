package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.utils.QuadExtractor;
import eu.neverblink.jelly.core.utils.TripleExtractor;
import java.util.function.BiConsumer;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.vocabulary.XSD;

public class Rdf4jEncoderConverter
    implements ProtoEncoderConverter<Value>, TripleExtractor<Value, Statement>, QuadExtractor<Value, Statement> {

    @Override
    public void nodeToProto(NodeEncoder<Value> encoder, Value value, BiConsumer<Object, Byte> consumer) {
        if (value instanceof IRI iri) {
            encoder.makeIri(iri.stringValue(), consumer);
        } else if (value instanceof BNode bNode) {
            encoder.makeBlankNode(bNode.getID(), consumer);
        } else if (value instanceof Literal literal) {
            final var lex = literal.getLabel();
            final var lang = literal.getLanguage();
            if (lang.isPresent()) {
                encoder.makeLangLiteral(literal, lex, lang.get(), consumer);
            } else {
                final var dt = literal.getDatatype();
                if (!dt.equals(XSD.STRING)) {
                    encoder.makeDtLiteral(literal, lex, dt.stringValue(), consumer);
                } else {
                    encoder.makeSimpleLiteral(lex, consumer);
                }
            }
        } else if (value instanceof Triple triple) {
            encoder.makeQuotedTriple(triple.getSubject(), triple.getPredicate(), triple.getObject(), consumer);
        } else {
            throw new RdfProtoSerializationError("Cannot encode node: %s".formatted(value));
        }
    }

    @Override
    public void graphNodeToProto(NodeEncoder<Value> encoder, Value value, BiConsumer<Object, Byte> consumer) {
        if (value instanceof IRI iri) {
            encoder.makeIri(iri.stringValue(), consumer);
        } else if (value instanceof BNode bNode) {
            encoder.makeBlankNode(bNode.getID(), consumer);
        } else if (value instanceof Literal literal) {
            final var lex = literal.getLabel();
            final var lang = literal.getLanguage();
            if (lang.isPresent()) {
                encoder.makeLangLiteral(literal, lex, lang.get(), consumer);
            } else {
                final var dt = literal.getDatatype();
                if (!dt.equals(XSD.STRING)) {
                    encoder.makeDtLiteral(literal, lex, dt.stringValue(), consumer);
                } else {
                    encoder.makeSimpleLiteral(lex, consumer);
                }
            }
        } else if (value == null) {
            encoder.makeDefaultGraph(consumer);
        } else {
            throw new RdfProtoSerializationError("Cannot encode graph node: %s".formatted(value));
        }
    }

    @Override
    public Value getQuadSubject(Statement statement) {
        return statement.getSubject();
    }

    @Override
    public Value getQuadPredicate(Statement statement) {
        return statement.getPredicate();
    }

    @Override
    public Value getQuadObject(Statement statement) {
        return statement.getObject();
    }

    @Override
    public Value getQuadGraph(Statement statement) {
        return statement.getContext();
    }

    @Override
    public Value getTripleSubject(Statement triple) {
        return triple.getSubject();
    }

    @Override
    public Value getTriplePredicate(Statement triple) {
        return triple.getPredicate();
    }

    @Override
    public Value getTripleObject(Statement triple) {
        return triple.getObject();
    }
}
