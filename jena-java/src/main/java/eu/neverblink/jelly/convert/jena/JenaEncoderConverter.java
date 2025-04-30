package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfTerm;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Quad;

public final class JenaEncoderConverter implements ProtoEncoderConverter<Node> {

    @Override
    public RdfTerm.SpoTerm nodeToProto(NodeEncoder<Node> encoder, Node node) {
        // URI/IRI
        if (node.isURI()) {
            return encoder.makeIri(node.getURI());
        }

        if (node.isBlank()) {
            // Blank node
            return encoder.makeBlankNode(node.getBlankNodeLabel());
        }

        if (node.isLiteral()) {
            // Literal
            final var lang = node.getLiteralLanguage();
            if (lang.isEmpty()) {
                // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
                if (node.getLiteralDatatype() == null) {
                    return encoder.makeSimpleLiteral(node.getLiteralLexicalForm());
                } else {
                    return encoder.makeDtLiteral(node, node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
                }
            } else {
                return encoder.makeLangLiteral(node, node.getLiteralLexicalForm(), lang);
            }
        }

        if (node.isNodeTriple()) {
            // RDF-star node
            final var t = node.getTriple();
            return encoder.makeQuotedTriple(
                nodeToProto(encoder, t.getSubject()),
                nodeToProto(encoder, t.getPredicate()),
                nodeToProto(encoder, t.getObject())
            );
        }

        throw new IllegalArgumentException("Cannot encode node: " + node);
    }

    @Override
    public RdfTerm.GraphTerm graphNodeToProto(NodeEncoder<Node> encoder, Node node) {
        // Default graph
        if (node == null) {
            return NodeEncoder.makeDefaultGraph();
        }

        // URI/IRI
        if (node.isURI()) {
            if (Quad.isDefaultGraph(node)) {
                return NodeEncoder.makeDefaultGraph();
            } else {
                return encoder.makeIri(node.getURI());
            }
        }

        // Blank node
        if (node.isBlank()) {
            return encoder.makeBlankNode(node.getBlankNodeLabel());
        }

        // Literal
        if (node.isLiteral()) {
            final var lang = node.getLiteralLanguage();
            if (lang.isEmpty()) {
                // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
                if (node.getLiteralDatatype() == null) {
                    return encoder.makeSimpleLiteral(node.getLiteralLexicalForm());
                } else {
                    return encoder.makeDtLiteral(node, node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
                }
            } else {
                return encoder.makeLangLiteral(node, node.getLiteralLexicalForm(), lang);
            }
        }

        throw new IllegalArgumentException("Cannot encode graph node: " + node);
    }
}
