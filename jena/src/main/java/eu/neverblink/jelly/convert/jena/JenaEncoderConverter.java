package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.utils.QuadExtractor;
import eu.neverblink.jelly.core.utils.TripleExtractor;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.TextDirection;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Quad;

public final class JenaEncoderConverter
    implements ProtoEncoderConverter<Node>, TripleExtractor<Node, Triple>, QuadExtractor<Node, Quad>
{

    @Override
    public void encodeIri(NodeEncoder<Node> encoder, Node node) {
        if (node.isURI()) {
            encoder.iri(node.getURI());
        } else {
            encodeAny(encoder, node);
        }
    }

    @Override
    public void encodeResource(NodeEncoder<Node> encoder, Node node) {
        if (node.isURI()) {
            encoder.iri(node.getURI());
        } else if (node.isBlank()) {
            encoder.blankNode(node.getBlankNodeLabel());
        } else {
            encodeAny(encoder, node);
        }
    }

    @Override
    public void encodeGraph(NodeEncoder<Node> encoder, Node node) {
        if (node == null || Quad.isDefaultGraph(node)) {
            encoder.defaultGraph();
        } else {
            encodeResource(encoder, node);
        }
    }

    @Override
    public void encodeAny(NodeEncoder<Node> encoder, Node node) {
        if (node.isURI()) {
            encoder.iri(node.getURI());
        } else if (node.isBlank()) {
            encoder.blankNode(node.getBlankNodeLabel());
        } else if (node.isLiteral()) {
            encodeLiteral(encoder, node);
        } else if (node.isTripleTerm()) {
            final var t = node.getTriple();
            encoder.tripleTerm(t.getSubject(), t.getPredicate(), t.getObject());
        } else {
            throw new RdfProtoSerializationError("Cannot encode node: " + node);
        }
    }

    private static void encodeLiteral(NodeEncoder<Node> encoder, Node node) {
        final var lang = node.getLiteralLanguage();
        if (lang.isEmpty()) {
            // RDF 1.1 spec: language tag MUST be non-empty. So, this is a plain or datatype literal.
            // We compare by reference, because the datatype is a singleton.
            if (node.getLiteralDatatype() == XSDDatatype.XSDstring) {
                encoder.simpleLiteral(node.getLiteralLexicalForm());
            } else {
                encoder.dtLiteral(node.getLiteralLexicalForm(), node.getLiteralDatatypeURI());
            }
        } else {
            final TextDirection direction = node.getLiteralBaseDirection();
            if (direction == null) {
                encoder.langLiteral(node.getLiteralLexicalForm(), lang);
            } else {
                encoder.dirLangLiteral(
                    node.getLiteralLexicalForm(),
                    lang,
                    direction == TextDirection.LTR ? RdfBaseDirection.LTR : RdfBaseDirection.RTL
                );
            }
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
