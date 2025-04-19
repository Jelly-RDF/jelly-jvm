package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoEncoderBase;
import eu.ostrzyciel.jelly.core.internal.RowBufferAppender;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.Collection;

public abstract class ProtoEncoder<TNode, TTriple, TQuad>
    extends ProtoEncoderBase<TNode, TTriple, TQuad>
    implements RowBufferAppender {

    public record Params(
        RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        Collection<RdfStreamRow> appendableRowBuffer
    ) {}

    protected final RdfStreamOptions options;
    protected final boolean enableNamespaceDeclarations;
    protected final Collection<RdfStreamRow> appendableRowBuffer;

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

    public final void addTripleStatement(TTriple triple) {
        addTripleStatement(converter.getTstS(triple), converter.getTstP(triple), converter.getTstO(triple));
    }

    public abstract void addTripleStatement(TNode subject, TNode predicate, TNode object);

    public final void addQuadStatement(TQuad quad) {
        addQuadStatement(
            converter.getQstS(quad),
            converter.getQstP(quad),
            converter.getQstO(quad),
            converter.getQstG(quad)
        );
    }

    public abstract void addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph);

    public abstract void startGraph(TNode graph);

    public abstract void startDefaultGraph();

    public abstract void endGraph();

    public abstract void declareNamespace(String name, String iriValue);
}
