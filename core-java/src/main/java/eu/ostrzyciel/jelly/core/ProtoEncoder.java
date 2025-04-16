package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoEncoderBase;
import eu.ostrzyciel.jelly.core.internal.RowBufferAppender;
import eu.ostrzyciel.jelly.core.proto.v1.Rdf;
import java.util.List;

public abstract class ProtoEncoder<TNode, TTriple, TQuad>
    extends ProtoEncoderBase<TNode, TTriple, TQuad>
    implements RowBufferAppender {

    public record Params(
        Rdf.RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        List<Rdf.RdfStreamRow> appendableRowBuffer
    ) {}

    protected final Rdf.RdfStreamOptions options;
    protected final boolean enableNamespaceDeclarations;
    protected final List<Rdf.RdfStreamRow> appendableRowBuffer;

    protected ProtoEncoder(
        NodeEncoder<TNode> nodeEncoder,
        ProtoEncoderConverter<TNode, TTriple, TQuad> converter,
        Params params
    ) {
        super(nodeEncoder, converter);
        this.options = params.options;
        this.enableNamespaceDeclarations = params.enableNamespaceDeclarations;
        this.appendableRowBuffer = params.appendableRowBuffer;
    }

    public final Iterable<Rdf.RdfStreamRow> addTripleStatement(TTriple triple) {
        return addTripleStatement(converter.getTstS(triple), converter.getTstP(triple), converter.getTstO(triple));
    }

    public abstract Iterable<Rdf.RdfStreamRow> addTripleStatement(TNode subject, TNode predicate, TNode object);

    public final Iterable<Rdf.RdfStreamRow> addQuadStatement(TQuad quad) {
        return addQuadStatement(
            converter.getQstS(quad),
            converter.getQstP(quad),
            converter.getQstO(quad),
            converter.getQstG(quad)
        );
    }

    public abstract Iterable<Rdf.RdfStreamRow> addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph);

    public abstract Iterable<Rdf.RdfStreamRow> startGraph(TNode graph);

    public abstract Iterable<Rdf.RdfStreamRow> startDefaultGraph();

    public abstract Iterable<Rdf.RdfStreamRow> endGraph();

    public abstract Iterable<Rdf.RdfStreamRow> declareNamespace(String name, String iriValue);
}
