package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.utils.QuadDecoder;
import eu.neverblink.jelly.core.utils.TripleDecoder;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.vocabulary.XSD;

public class Rdf4jEncoderConverter
    implements ProtoEncoderConverter<Value>, TripleDecoder<Value, Statement>, QuadDecoder<Value, Statement> {

    @Override
    public void nodeToProto(NodeEncoder<Value> encoder, Value value) {
        if (value instanceof IRI iri) {
            encoder.makeIri(iri.stringValue());
        } else if (value instanceof BNode bNode) {
            encoder.makeBlankNode(bNode.getID());
        } else if (value instanceof Literal literal) {
            final var lex = literal.getLabel();
            final var lang = literal.getLanguage();
            if (lang.isPresent()) {
                encoder.makeLangLiteral(literal, lex, lang.get());
            } else {
                final var dt = literal.getDatatype();
                if (dt != XSD.STRING) {
                    encoder.makeDtLiteral(literal, lex, dt.stringValue());
                } else {
                    encoder.makeSimpleLiteral(lex);
                }
            }
        } else if (value instanceof Triple triple) {
            encoder.makeQuotedTriple(triple.getSubject(), triple.getPredicate(), triple.getObject());
        } else {
            throw new RdfProtoSerializationError("Cannot encode node: %s".formatted(value));
        }
    }

    @Override
    public void graphNodeToProto(NodeEncoder<Value> encoder, Value value) {
        if (value instanceof IRI iri) {
            encoder.makeIri(iri.stringValue());
        } else if (value instanceof BNode bNode) {
            encoder.makeBlankNode(bNode.getID());
        } else if (value instanceof Literal literal) {
            final var lex = literal.getLabel();
            final var lang = literal.getLanguage();
            if (lang.isPresent()) {
                encoder.makeLangLiteral(literal, lex, lang.get());
            } else {
                final var dt = literal.getDatatype();
                if (dt != XSD.STRING) {
                    encoder.makeDtLiteral(literal, lex, dt.stringValue());
                } else {
                    encoder.makeSimpleLiteral(lex);
                }
            }
        } else if (value == null) {
            encoder.makeDefaultGraph();
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
