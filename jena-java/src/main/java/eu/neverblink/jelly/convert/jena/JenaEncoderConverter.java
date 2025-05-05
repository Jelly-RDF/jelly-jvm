package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Quad;

public final class JenaEncoderConverter implements ProtoEncoderConverter<Node> {

    @Override
    public void nodeToProto(NodeEncoder<Node> encoder, Node node) {
        // URI/IRI
        if (node.isURI()) {
            encoder.makeIri(node.getURI());
        } else if (node.isBlank()) {
            // Blank node
            encoder.makeBlankNode(node.getBlankNodeLabel());
        } else if (node.isLiteral()) {
            // Literal
            final var lang = node.getLiteralLanguage();
            if (lang.isEmpty()) {
                // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
                if (node.getLiteralDatatype() == null) {
                    encoder.makeSimpleLiteral(node.getLiteralLexicalForm());
                } else {
                    encoder.makeDtLiteral(node, node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
                }
            } else {
                encoder.makeLangLiteral(node, node.getLiteralLexicalForm(), lang);
            }
        } else if (node.isNodeTriple()) {
            // RDF-star node
            final var t = node.getTriple();
            encoder.makeQuotedTriple(t.getSubject(), t.getPredicate(), t.getObject());
        } else {
            throw new IllegalArgumentException("Cannot encode node: " + node);
        }
    }

    @Override
    public void graphNodeToProto(NodeEncoder<Node> encoder, Node node) {
        // Default graph
        if (node == null) {
            encoder.makeDefaultGraph();
        } else if (node.isURI()) {
            // URI/IRI
            if (Quad.isDefaultGraph(node)) {
                encoder.makeDefaultGraph();
            } else {
                encoder.makeIri(node.getURI());
            }
        } else if (node.isBlank()) {
            // Blank node
            encoder.makeBlankNode(node.getBlankNodeLabel());
        } else if (node.isLiteral()) {
            // Literal
            final var lang = node.getLiteralLanguage();
            if (lang.isEmpty()) {
                // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
                if (node.getLiteralDatatype() == null) {
                    encoder.makeSimpleLiteral(node.getLiteralLexicalForm());
                } else {
                    encoder.makeDtLiteral(node, node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
                }
            } else {
                encoder.makeLangLiteral(node, node.getLiteralLexicalForm(), lang);
            }
        } else {
            throw new IllegalArgumentException("Cannot encode graph node: " + node);
        }
    }
}
