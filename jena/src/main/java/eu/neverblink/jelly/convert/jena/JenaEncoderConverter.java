package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.utils.QuadExtractor;
import eu.neverblink.jelly.core.utils.TripleExtractor;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.JenaCompatHelper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Quad;

public final class JenaEncoderConverter
    implements ProtoEncoderConverter<Node>, TripleExtractor<Node, Triple>, QuadExtractor<Node, Quad>
{

    @Override
    public Object nodeToProto(NodeEncoder<Node> encoder, Node node) {
        // URI/IRI
        if (node.isURI()) {
            return encoder.makeIri(node.getURI());
        } else if (node.isBlank()) {
            // Blank node
            return encoder.makeBlankNode(node.getBlankNodeLabel());
        } else if (node.isLiteral()) {
            // Literal
            final var lang = node.getLiteralLanguage();
            if (lang.isEmpty()) {
                // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
                // We compare by reference, because the datatype is a singleton.
                if (node.getLiteralDatatype() == XSDDatatype.XSDstring) {
                    return encoder.makeSimpleLiteral(node.getLiteralLexicalForm());
                } else {
                    return encoder.makeDtLiteral(node, node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
                }
            } else {
                return encoder.makeLangLiteral(node, node.getLiteralLexicalForm(), lang);
            }
        } else if (JenaCompatHelper.isNodeTriple(node)) {
            // RDF-star node
            final var t = node.getTriple();
            return encoder.makeQuotedTriple(t.getSubject(), t.getPredicate(), t.getObject());
        } else {
            throw new IllegalArgumentException("Cannot encode node: " + node);
        }
    }

    @Override
    public Object graphNodeToProto(NodeEncoder<Node> encoder, Node node) {
        // Default graph
        if (node == null) {
            return encoder.makeDefaultGraph();
        } else if (node.isURI()) {
            // URI/IRI
            if (Quad.isDefaultGraph(node)) {
                return encoder.makeDefaultGraph();
            } else {
                return encoder.makeIri(node.getURI());
            }
        } else if (node.isBlank()) {
            // Blank node
            return encoder.makeBlankNode(node.getBlankNodeLabel());
        } else if (node.isLiteral()) {
            // Literal
            final var lang = node.getLiteralLanguage();
            if (lang.isEmpty()) {
                // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
                // We compare by reference, because the datatype is a singleton.
                if (node.getLiteralDatatype() == XSDDatatype.XSDstring) {
                    return encoder.makeSimpleLiteral(node.getLiteralLexicalForm());
                } else {
                    return encoder.makeDtLiteral(node, node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
                }
            } else {
                return encoder.makeLangLiteral(node, node.getLiteralLexicalForm(), lang);
            }
        } else {
            throw new IllegalArgumentException("Cannot encode graph node: " + node);
        }
    }

    @Override
    public Node getQuadSubject(Quad quad) {
        return quad.getSubject();
    }

    @Override
    public Node getQuadPredicate(Quad quad) {
        return quad.getPredicate();
    }

    @Override
    public Node getQuadObject(Quad quad) {
        return quad.getObject();
    }

    @Override
    public Node getQuadGraph(Quad quad) {
        return quad.getGraph();
    }

    @Override
    public Node getTripleSubject(Triple triple) {
        return triple.getSubject();
    }

    @Override
    public Node getTriplePredicate(Triple triple) {
        return triple.getPredicate();
    }

    @Override
    public Node getTripleObject(Triple triple) {
        return triple.getObject();
    }
}
