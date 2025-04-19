package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoEncoderBase;
import eu.ostrzyciel.jelly.core.internal.RowBufferAppender;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.List;

public abstract class ProtoEncoder<TNode, TTriple, TQuad>
    extends ProtoEncoderBase<TNode, TTriple, TQuad>
    implements RowBufferAppender {

    public record Params(
        RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        List<RdfStreamRow> appendableRowBuffer
    ) {}

    protected final RdfStreamOptions options;
    protected final boolean enableNamespaceDeclarations;
    protected final List<RdfStreamRow> appendableRowBuffer;

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

    public final Iterable<RdfStreamRow> addTripleStatement(TTriple triple) {
        return addTripleStatement(converter.getTstS(triple), converter.getTstP(triple), converter.getTstO(triple));
    }

    public abstract Iterable<RdfStreamRow> addTripleStatement(TNode subject, TNode predicate, TNode object);

    public final Iterable<RdfStreamRow> addQuadStatement(TQuad quad) {
        return addQuadStatement(
            converter.getQstS(quad),
            converter.getQstP(quad),
            converter.getQstO(quad),
            converter.getQstG(quad)
        );
    }

    public abstract Iterable<RdfStreamRow> addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph);

    public abstract Iterable<RdfStreamRow> startGraph(TNode graph);

    public abstract Iterable<RdfStreamRow> startDefaultGraph();

    public abstract Iterable<RdfStreamRow> endGraph();

    public abstract Iterable<RdfStreamRow> declareNamespace(String name, String iriValue);
}
