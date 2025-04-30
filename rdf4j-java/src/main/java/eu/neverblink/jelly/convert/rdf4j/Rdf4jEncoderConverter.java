package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.RdfTerm;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.vocabulary.XSD;

public class Rdf4jEncoderConverter implements ProtoEncoderConverter<Value> {

    @Override
    public RdfTerm.SpoTerm nodeToProto(NodeEncoder<Value> encoder, Value value) {
        if (value instanceof IRI iri) {
            return encoder.makeIri(iri.stringValue());
        } else if (value instanceof BNode bNode) {
            return encoder.makeBlankNode(bNode.getID());
        } else if (value instanceof Literal literal) {
            final var lex = literal.getLabel();
            final var lang = literal.getLanguage();
            if (lang.isPresent()) {
                return encoder.makeLangLiteral(literal, lex, lang.get());
            }

            final var dt = literal.getDatatype();
            if (dt != XSD.STRING) {
                return encoder.makeDtLiteral(literal, lex, dt.stringValue());
            }

            return encoder.makeSimpleLiteral(lex);
        } else if (value instanceof Triple triple) {
            return encoder.makeQuotedTriple(
                nodeToProto(encoder, triple.getSubject()),
                nodeToProto(encoder, triple.getPredicate()),
                nodeToProto(encoder, triple.getObject())
            );
        } else {
            throw new RdfProtoSerializationError("Cannot encode node: %s".formatted(value));
        }
    }

    @Override
    public RdfTerm.GraphTerm graphNodeToProto(NodeEncoder<Value> encoder, Value value) {
        if (value instanceof IRI iri) {
            return encoder.makeIri(iri.stringValue());
        } else if (value instanceof BNode bNode) {
            return encoder.makeBlankNode(bNode.getID());
        } else if (value instanceof Literal literal) {
            final var lex = literal.getLabel();
            final var lang = literal.getLanguage();
            if (lang.isPresent()) {
                return encoder.makeLangLiteral(literal, lex, lang.get());
            }

            final var dt = literal.getDatatype();
            if (dt != XSD.STRING) {
                return encoder.makeDtLiteral(literal, lex, dt.stringValue());
            }

            return encoder.makeSimpleLiteral(lex);
        } else if (value == null) {
            return NodeEncoder.makeDefaultGraph();
        } else {
            throw new RdfProtoSerializationError("Cannot encode graph node: %s".formatted(value));
        }
    }
}
