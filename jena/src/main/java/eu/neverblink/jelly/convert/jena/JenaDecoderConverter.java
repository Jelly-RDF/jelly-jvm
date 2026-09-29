package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.utils.QuadMaker;
import eu.neverblink.jelly.core.utils.TripleMaker;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.TextDirection;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.LiteralLabelFactory;
import org.apache.jena.langtagx.LangTagX;
import org.apache.jena.sparql.core.Quad;

public final class JenaDecoderConverter
    implements ProtoDecoderConverter<Node, RDFDatatype>, TripleMaker<Node, Triple>, QuadMaker<Node, Quad>
{

    @Override
    public Node makeSimpleLiteral(String lex) {
        return NodeFactory.createLiteralString(lex);
    }

    /** A language tag as the decoder passed it, and as Jena formats it. */
    private record FormattedLangtag(String tag, String formatted) {}

    // The last language tag formatted. Literals with the same tag tend to come one after another,
    // and Jena parses and formats the tag again for every literal. A single immutable pair keeps
    // this safe when the converter is shared between threads: a race only formats a tag twice.
    private FormattedLangtag lastLangtag = new FormattedLangtag("", "");

    @Override
    public Node makeLangLiteral(String lex, String lang) {
        FormattedLangtag last = lastLangtag;
        if (!last.tag.equals(lang)) {
            // An empty tag, or one with a base direction ("en--ltr"), is not a plain language tag
            if (lang.isEmpty() || lang.contains("--")) {
                return NodeFactory.createLiteralLang(lex, lang);
            }
            last = new FormattedLangtag(lang, LangTagX.formatLanguageTag(lang));
            lastLangtag = last;
        }
        // The same literal as NodeFactory.createLiteralLang(lex, lang) makes
        return NodeFactory.createLiteral(LiteralLabelFactory.createLang(lex, last.formatted));
    }

    @Override
    public Node makeDirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
        return NodeFactory.createLiteralDirLang(
            lex,
            lang,
            direction == RdfBaseDirection.LTR ? TextDirection.LTR : TextDirection.RTL
        );
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
        return NodeFactory.createTripleTerm(s, p, o);
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
