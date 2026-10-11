package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.utils.QuadExtractor;
import eu.neverblink.jelly.core.utils.TripleExtractor;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.vocabulary.XSD;

public final class Rdf4jEncoderConverter
    implements ProtoEncoderConverter<Value>, TripleExtractor<Value, Statement>, QuadExtractor<Value, Statement>
{

    // isIRI, isBNode etc. were found to be faster than instanceof checks. instanceof over interfaces
    // is very slow if you use it with multiple interfaces – only the last interface check is cached.

    @Override
    public void encodeIri(NodeEncoder<Value> encoder, Value value) {
        if (value.isIRI()) {
            encoder.iri(value.stringValue());
        } else {
            encodeAny(encoder, value);
        }
    }

    @Override
    public void encodeResource(NodeEncoder<Value> encoder, Value value) {
        if (value.isIRI()) {
            encoder.iri(value.stringValue());
        } else if (value.isBNode()) {
            encoder.blankNode(((BNode) value).getID());
        } else {
            encodeAny(encoder, value);
        }
    }

    @Override
    public void encodeGraph(NodeEncoder<Value> encoder, Value value) {
        if (value == null) {
            encoder.defaultGraph();
        } else {
            encodeResource(encoder, value);
        }
    }

    @Override
    public void encodeAny(NodeEncoder<Value> encoder, Value value) {
        if (value.isIRI()) {
            encoder.iri(value.stringValue());
        } else if (value.isBNode()) {
            encoder.blankNode(((BNode) value).getID());
        } else if (value instanceof Literal literal) {
            encodeLiteral(encoder, literal);
        } else if (value instanceof TripleTerm tripleTerm) {
            encoder.tripleTerm(tripleTerm.getSubject(), tripleTerm.getPredicate(), tripleTerm.getObject());
        } else {
            throw new RdfProtoSerializationError("Cannot encode node: %s".formatted(value));
        }
    }

    private static void encodeLiteral(NodeEncoder<Value> encoder, Literal literal) {
        final var lex = literal.getLabel();
        final var lang = literal.getLanguage();
        if (lang.isPresent()) {
            final Literal.BaseDirection direction = literal.getBaseDirection();
            if (direction == Literal.BaseDirection.NONE) {
                encoder.langLiteral(lex, lang.get());
            } else {
                encoder.dirLangLiteral(
                    lex,
                    lang.get(),
                    direction == Literal.BaseDirection.LTR ? RdfBaseDirection.LTR : RdfBaseDirection.RTL
                );
            }
        } else {
            final var dt = literal.getDatatype();
            if (dt.equals(XSD.STRING)) {
                encoder.simpleLiteral(lex);
            } else {
                encoder.dtLiteral(lex, dt.stringValue());
            }
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
