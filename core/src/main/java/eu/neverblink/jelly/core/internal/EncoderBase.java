package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.internal.proto.*;
import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Base interface for Jelly proto encoders. Only for internal use.
 * @param <TNode> type of RDF nodes in the library
 */
@InternalApi
public abstract class EncoderBase<TNode> implements RdfBufferAppender {

    protected final ProtoEncoderConverter<TNode> converter;
    private RowNodeEncoder<TNode> nodeEncoder;

    protected TNode lastSubject = null;
    protected TNode lastPredicate = null;
    protected TNode lastObject = null;

    protected boolean lastGraphSet = false;
    protected TNode lastGraph = null;

    protected EncoderBase(ProtoEncoderConverter<TNode> converter) {
        this.converter = converter;
    }

    protected final RowNodeEncoder<TNode> getNodeEncoder() {
        if (nodeEncoder == null) {
            nodeEncoder = new RowNodeEncoder<>(
                converter,
                this,
                getPrefixTableSize(),
                getNameTableSize(),
                getDatatypeTableSize()
            );
        }
        return nodeEncoder;
    }

    protected abstract int getNameTableSize();

    protected abstract int getPrefixTableSize();

    protected abstract int getDatatypeTableSize();

    /**
     * Should return a new instance of the RdfTriple class, via the used allocator.
     * @return a new RdfTriple instance
     */
    protected abstract RdfTriple.Mutable newTriple();

    /**
     * Should return a new instance of the RdfQuad class, via the used allocator.
     * @return a new RdfQuad instance
     */
    protected abstract RdfQuad.Mutable newQuad();

    protected final RdfTriple tripleToProto(TNode subject, TNode predicate, TNode object) {
        getNodeEncoder().newEpoch();
        final RdfTriple.Mutable triple = newTriple();
        subjectNodeToProtoWrapped(triple, subject);
        predicateNodeToProtoWrapped(triple, predicate);
        objectNodeToProtoWrapped(triple, object);
        return triple;
    }

    protected final RdfQuad quadToProto(TNode subject, TNode predicate, TNode object, TNode graph) {
        getNodeEncoder().newEpoch();
        final RdfQuad.Mutable quad = newQuad();
        subjectNodeToProtoWrapped(quad, subject);
        predicateNodeToProtoWrapped(quad, predicate);
        objectNodeToProtoWrapped(quad, object);
        graphNodeToProtoWrapped(quad, graph);
        return quad;
    }

    /**
     * Converts a triple to an RdfQuad object with a null graph.
     * <p>
     * Used in RDF-Patch for triple add/delete operations.
     */
    protected final RdfQuad tripleInQuadToProto(TNode subject, TNode predicate, TNode object) {
        getNodeEncoder().newEpoch();
        final RdfQuad.Mutable quad = newQuad();
        subjectNodeToProtoWrapped(quad, subject);
        predicateNodeToProtoWrapped(quad, predicate);
        objectNodeToProtoWrapped(quad, object);
        return quad;
    }

    /**
     * Converts a graph term to an RdfGraphStart object.
     */
    protected final RdfGraphStart graphStartToProto(TNode graph) {
        getNodeEncoder().newEpoch();
        final RdfGraphStart.Mutable graphStart = RdfGraphStart.newInstance();
        graphStart.setGraph(getNodeEncoder().encodeGraph(graph));
        return graphStart;
    }

    private void subjectNodeToProtoWrapped(SpoBase.Setters target, TNode node) {
        if (!node.equals(lastSubject)) {
            lastSubject = node;
            target.setSubject(getNodeEncoder().encodeResource(node));
        }
    }

    private void predicateNodeToProtoWrapped(SpoBase.Setters target, TNode node) {
        if (!node.equals(lastPredicate)) {
            lastPredicate = node;
            target.setPredicate(getNodeEncoder().encodeIri(node));
        }
    }

    private void objectNodeToProtoWrapped(SpoBase.Setters target, TNode node) {
        if (!node.equals(lastObject)) {
            lastObject = node;
            target.setObject(getNodeEncoder().encodeAny(node));
        }
    }

    protected final void graphNodeToProtoWrapped(GraphBase.Setters target, TNode node) {
        // Graph nodes may be null in Jena for example... so we need to handle that.
        if ((lastGraphSet && node == null && lastGraph == null) || (node != null && node.equals(lastGraph))) {
            return;
        }

        lastGraphSet = true;
        lastGraph = node;
        target.setGraph(getNodeEncoder().encodeGraph(node));
    }
}
