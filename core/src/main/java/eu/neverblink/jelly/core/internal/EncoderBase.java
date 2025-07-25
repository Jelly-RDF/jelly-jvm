package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.internal.proto.*;
import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Base interface for Jelly proto encoders. Only for internal use.
 * @param <TNode> type of RDF nodes in the library
 */
@InternalApi
public abstract class EncoderBase<TNode> implements RdfBufferAppender<TNode> {

    protected final ProtoEncoderConverter<TNode> converter;
    private NodeEncoder<TNode> nodeEncoder;

    protected TNode lastSubject = null;
    protected TNode lastPredicate = null;
    protected TNode lastObject = null;

    protected boolean lastGraphSet = false;
    protected TNode lastGraph = null;

    protected EncoderBase(ProtoEncoderConverter<TNode> converter) {
        this.converter = converter;
    }

    protected final NodeEncoder<TNode> getNodeEncoder() {
        if (nodeEncoder == null) {
            nodeEncoder = NodeEncoderImpl.create(
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
        final RdfTriple.Mutable triple = newTriple();
        subjectNodeToProtoWrapped(triple, subject);
        predicateNodeToProtoWrapped(triple, predicate);
        objectNodeToProtoWrapped(triple, object);
        return triple;
    }

    protected final RdfQuad quadToProto(TNode subject, TNode predicate, TNode object, TNode graph) {
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
        final RdfGraphStart.Mutable graphStart = RdfGraphStart.newInstance();
        final var encoded = converter.graphNodeToProto(getNodeEncoder(), graph);
        setGraphNode(graphStart, encoded);
        return graphStart;
    }

    private void subjectNodeToProtoWrapped(SpoBase.Setters target, TNode node) {
        if (!node.equals(lastSubject)) {
            lastSubject = node;
            final var encoded = converter.nodeToProto(getNodeEncoder(), node);
            // Shortcut: for subject nodes, TERM_* constants align with field numbers.
            target.setSubject(encoded.node(), encoded.termType());
        }
    }

    private void predicateNodeToProtoWrapped(SpoBase.Setters target, TNode node) {
        if (!node.equals(lastPredicate)) {
            lastPredicate = node;
            final var encoded = converter.nodeToProto(getNodeEncoder(), node);
            // Shortcut: for predicate nodes, TERM_* constants can be simply offset.
            target.setPredicate(encoded.node(), (byte) (encoded.termType() + RdfTriple.P_IRI - 1));
        }
    }

    private void objectNodeToProtoWrapped(SpoBase.Setters target, TNode node) {
        if (!node.equals(lastObject)) {
            lastObject = node;
            final var encoded = converter.nodeToProto(getNodeEncoder(), node);
            target.setObject(encoded.node(), (byte) (encoded.termType() + RdfTriple.O_IRI - 1));
        }
    }

    protected final void graphNodeToProtoWrapped(GraphBase.Setters target, TNode node) {
        // Graph nodes may be null in Jena for example... so we need to handle that.
        if ((lastGraphSet && node == null && lastGraph == null) || (node != null && node.equals(lastGraph))) {
            return;
        }

        lastGraphSet = true;
        lastGraph = node;
        final var encoded = converter.graphNodeToProto(getNodeEncoder(), node);
        setGraphNode(target, encoded);
    }

    protected final void setGraphNode(GraphBase.Setters target, Encoded encoded) {
        switch (encoded.termType()) {
            case RdfBufferAppender.TERM_IRI -> target.setGIri((RdfIri) encoded.node());
            case RdfBufferAppender.TERM_BNODE -> target.setGBnode((String) encoded.node());
            case RdfBufferAppender.TERM_LITERAL -> target.setGLiteral((RdfLiteral) encoded.node());
            case RdfBufferAppender.TERM_DEFAULT_GRAPH -> target.setGDefaultGraph((RdfDefaultGraph) encoded.node());
            default -> throw new RdfProtoSerializationError("Unexpected graph node kind: " + encoded.termType());
        }
    }

    @Override
    public RdfBufferAppender.Encoded appendQuotedTriple(TNode subject, TNode predicate, TNode object) {
        // Encode the quoted triple
        final RdfTriple.Mutable quotedTriple = RdfTriple.newInstance();
        final var nodeEncoder = getNodeEncoder();
        final var s = converter.nodeToProto(nodeEncoder, subject);
        quotedTriple.setSubject(s.node(), s.termType());
        final var p = converter.nodeToProto(nodeEncoder, predicate);
        quotedTriple.setPredicate(p.node(), (byte) (p.termType() + RdfTriple.P_IRI - 1));
        final var o = converter.nodeToProto(nodeEncoder, object);
        quotedTriple.setObject(o.node(), (byte) (o.termType() + RdfTriple.O_IRI - 1));
        return new Encoded(quotedTriple, RdfBufferAppender.TERM_TRIPLE);
    }
}
