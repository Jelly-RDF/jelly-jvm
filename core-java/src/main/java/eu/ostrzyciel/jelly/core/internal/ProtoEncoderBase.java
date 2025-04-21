package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.NodeEncoder;
import eu.ostrzyciel.jelly.core.ProtoEncoderConverter;
import eu.ostrzyciel.jelly.core.RdfTerm;
import eu.ostrzyciel.jelly.core.RowBufferAppender;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;

/**
 * Base interface for Jelly proto encoders. Only for internal use.
 * @param <TNode> type of RDF nodes in the library
 */
public abstract class ProtoEncoderBase<TNode> implements RowBufferAppender {

    /**
     * RdfStreamOptions for this encoder.
     */
    protected final RdfStreamOptions options;
    protected final NodeEncoder<TNode> nodeEncoder;
    protected final ProtoEncoderConverter<TNode> converter;

    protected final LastNodeHolder<TNode> lastSubject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastPredicate = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastObject = new LastNodeHolder<>();
    protected TNode lastGraph = null;

    protected ProtoEncoderBase(RdfStreamOptions options, ProtoEncoderConverter<TNode> converter) {
        this.options = options;
        this.nodeEncoder = NodeEncoderImpl.create(options, this);
        this.converter = converter;
    }

    protected final RdfTerm.Triple tripleToProto(TNode subject, TNode predicate, TNode object) {
        return new RdfTerm.Triple(
            nodeToProtoWrapped(subject, lastSubject),
            nodeToProtoWrapped(predicate, lastPredicate),
            nodeToProtoWrapped(object, lastObject)
        );
    }

    protected final RdfTerm.Quad quadToProto(TNode subject, TNode predicate, TNode object, TNode graph) {
        return new RdfTerm.Quad(
            nodeToProtoWrapped(subject, lastSubject),
            nodeToProtoWrapped(predicate, lastPredicate),
            nodeToProtoWrapped(object, lastObject),
            graphNodeToProtoWrapped(graph)
        );
    }

    /**
     * Converts a triple to an RdfQuad object with a null graph.
     * <p>
     * Used in RDF-Patch for triple add/delete operations.
     */
    protected final RdfTerm.Quad tripleInQuadToProto(TNode subject, TNode predicate, TNode object) {
        return new RdfTerm.Quad(
            nodeToProtoWrapped(subject, lastSubject),
            nodeToProtoWrapped(predicate, lastPredicate),
            nodeToProtoWrapped(object, lastObject),
            null
        );
    }

    private RdfTerm.SpoTerm nodeToProtoWrapped(TNode node, LastNodeHolder<TNode> lastNodeHolder) {
        if (node.equals(lastNodeHolder.node)) {
            return null;
        } else {
            lastNodeHolder.node = node;
            return converter.nodeToProto(nodeEncoder, node);
        }
    }

    private RdfTerm.GraphTerm graphNodeToProtoWrapped(TNode node) {
        // Graph nodes may be null in Jena for example... so we need to handle that.
        if ((node == null && lastGraph == null) || (node != null && node.equals(lastGraph))) {
            return null;
        } else {
            lastGraph = node;
            return converter.graphNodeToProto(nodeEncoder, node);
        }
    }
}
