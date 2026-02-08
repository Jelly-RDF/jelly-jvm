package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.utils.QuadMaker;
import eu.neverblink.jelly.core.utils.TripleMaker;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Quad;

public final class JenaDecoderConverter
    implements ProtoDecoderConverter<Node, RDFDatatype>, TripleMaker<Node, Triple>, QuadMaker<Node, Quad>
{

    @Override
    public Node makeSimpleLiteral(String lex) {
        return NodeFactory.createLiteralString(lex);
    }

    @Override
    public Node makeLangLiteral(String lex, String lang) {
        return NodeFactory.createLiteralLang(lex, lang);
    }

    @Override
    public Node makeDtLiteral(String lex, RDFDatatype dt) {
        return NodeFactory.createLiteralDT(lex, dt);
    }

    @Override
    public RDFDatatype makeDatatype(String dt) {
        return NodeFactory.getType(dt);
    }

    @Override
    public Node makeBlankNode(String label) {
        return NodeFactory.createBlankNode(label);
    }

    @Override
    public Node makeIriNode(String iri) {
        return NodeFactory.createURI(iri);
    }

    @Override
    public Node makeTripleNode(Node s, Node p, Node o) {
        return JenaCompatHelper.getInstance().createTripleNode(s, p, o);
    }

    @Override
    public Node makeDefaultGraphNode() {
        return Quad.defaultGraphNodeGenerated;
    }

    @Override
    public Quad makeQuad(Node subject, Node predicate, Node object, Node graph) {
        return Quad.create(graph, subject, predicate, object);
    }

    @Override
    public Triple makeTriple(Node subject, Node predicate, Node object) {
        return Triple.create(subject, predicate, object);
    }
}
