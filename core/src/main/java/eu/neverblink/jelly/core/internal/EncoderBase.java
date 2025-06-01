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

    protected enum SpoTerm {
        SUBJECT,
        PREDICATE,
        OBJECT,
        GRAPH,
        NAMESPACE,
        HEADER,
    }

    protected final ProtoEncoderConverter<TNode> converter;
    private NodeEncoder<TNode> nodeEncoder;

    protected TNode lastSubject = null;
    protected TNode lastPredicate = null;
    protected TNode lastObject = null;

    protected boolean lastGraphSet = false;
    protected TNode lastGraph = null;

    protected SpoTerm currentTerm = SpoTerm.SUBJECT;
    private SpoBase.Setters currentSpoBase = null;
    protected GraphBase.Setters currentGraphBase = null;
    protected NsBase.Setters currentNsBase = null;
    protected HeaderBase.Setters currentHeaderBase = null;

    protected EncoderBase(ProtoEncoderConverter<TNode> converter) {
        this.converter = converter;
    }

    protected final NodeEncoder<TNode> getNodeEncoder() {
        if (nodeEncoder == null) {
            nodeEncoder = NodeEncoderImpl.create(this, getPrefixTableSize(), getNameTableSize(), getDatatypeTableSize());
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
        this.currentSpoBase = triple;
        subjectNodeToProtoWrapped(subject);
        predicateNodeToProtoWrapped(predicate);
        objectNodeToProtoWrapped(object);
        return triple;
    }

    protected final RdfQuad quadToProto(TNode subject, TNode predicate, TNode object, TNode graph) {
        final RdfQuad.Mutable quad = newQuad();
        this.currentSpoBase = quad;
        this.currentGraphBase = quad;
        subjectNodeToProtoWrapped(subject);
        predicateNodeToProtoWrapped(predicate);
        objectNodeToProtoWrapped(object);
        graphNodeToProtoWrapped(graph);
        return quad;
    }

    /**
     * Converts a triple to an RdfQuad object with a null graph.
     * <p>
     * Used in RDF-Patch for triple add/delete operations.
     */
    protected final RdfQuad tripleInQuadToProto(TNode subject, TNode predicate, TNode object) {
        final RdfQuad.Mutable quad = newQuad();
        this.currentSpoBase = quad;
        subjectNodeToProtoWrapped(subject);
        predicateNodeToProtoWrapped(predicate);
        objectNodeToProtoWrapped(object);
        return quad;
    }

    /**
     * Converts a graph term to an RdfGraphStart object.
     */
    protected final RdfGraphStart graphStartToProto(TNode graph) {
        final RdfGraphStart.Mutable graphStart = RdfGraphStart.newInstance();
        this.currentGraphBase = graphStart;
        currentTerm = SpoTerm.GRAPH;
        converter.graphNodeToProto(getNodeEncoder(), graph);
        return graphStart;
    }

    private void subjectNodeToProtoWrapped(TNode node) {
        if (!node.equals(lastSubject)) {
            lastSubject = node;
            currentTerm = SpoTerm.SUBJECT;
            converter.nodeToProto(getNodeEncoder(), node);
        }
    }

    private void predicateNodeToProtoWrapped(TNode node) {
        if (!node.equals(lastPredicate)) {
            lastPredicate = node;
            currentTerm = SpoTerm.PREDICATE;
            converter.nodeToProto(getNodeEncoder(), node);
        }
    }

    private void objectNodeToProtoWrapped(TNode node) {
        if (!node.equals(lastObject)) {
            lastObject = node;
            currentTerm = SpoTerm.OBJECT;
            converter.nodeToProto(getNodeEncoder(), node);
        }
    }

    protected final void graphNodeToProtoWrapped(TNode node) {
        // Graph nodes may be null in Jena for example... so we need to handle that.
        if ((lastGraphSet && node == null && lastGraph == null) || (node != null && node.equals(lastGraph))) {
            return;
        }

        lastGraphSet = true;
        lastGraph = node;
        currentTerm = SpoTerm.GRAPH;
        converter.graphNodeToProto(getNodeEncoder(), node);
    }

    @Override
    public void appendIri(RdfIri iri) {
        switch (currentTerm) {
            case SUBJECT -> currentSpoBase.setSIri(iri);
            case PREDICATE -> currentSpoBase.setPIri(iri);
            case OBJECT -> currentSpoBase.setOIri(iri);
            case GRAPH -> currentGraphBase.setGIri(iri);
            case NAMESPACE -> currentNsBase.setValue(iri);
            case HEADER -> currentHeaderBase.setHIri(iri);
        }
    }

    @Override
    public void appendBlankNode(String label) {
        switch (currentTerm) {
            case SUBJECT -> currentSpoBase.setSBnode(label);
            case PREDICATE -> currentSpoBase.setPBnode(label);
            case OBJECT -> currentSpoBase.setOBnode(label);
            case GRAPH -> currentGraphBase.setGBnode(label);
            case HEADER -> currentHeaderBase.setHBnode(label);
        }
    }

    @Override
    public void appendLiteral(RdfLiteral literal) {
        switch (currentTerm) {
            case SUBJECT -> currentSpoBase.setSLiteral(literal);
            case PREDICATE -> currentSpoBase.setPLiteral(literal);
            case OBJECT -> currentSpoBase.setOLiteral(literal);
            case GRAPH -> currentGraphBase.setGLiteral(literal);
            case HEADER -> currentHeaderBase.setHLiteral(literal);
        }
    }

    @Override
    public void appendQuotedTriple(TNode subject, TNode predicate, TNode object) {
        // Store the current state of the SpoBase and SpoTerm
        final SpoBase.Setters parent = currentSpoBase;
        final SpoTerm parentTerm = currentTerm;
        // Encode the quoted triple
        final RdfTriple.Mutable quotedTriple = RdfTriple.newInstance();
        currentSpoBase = quotedTriple;
        final var nodeEncoder = getNodeEncoder();
        currentTerm = SpoTerm.SUBJECT;
        converter.nodeToProto(nodeEncoder, subject);
        currentTerm = SpoTerm.PREDICATE;
        converter.nodeToProto(nodeEncoder, predicate);
        currentTerm = SpoTerm.OBJECT;
        converter.nodeToProto(nodeEncoder, object);
        // Restore the previous state and set the quoted triple
        currentSpoBase = parent;
        currentTerm = parentTerm;
        switch (currentTerm) {
            case SUBJECT -> currentSpoBase.setSTripleTerm(quotedTriple);
            case PREDICATE -> currentSpoBase.setPTripleTerm(quotedTriple);
            case OBJECT -> currentSpoBase.setOTripleTerm(quotedTriple);
            case GRAPH -> throw new RdfProtoSerializationError("Cannot set a graph node to be a quoted triple.");
            case HEADER -> currentHeaderBase.setHTripleTerm(quotedTriple);
        }
    }

    @Override
    public void appendDefaultGraph() {
        currentGraphBase.setGDefaultGraph(RdfDefaultGraph.EMPTY);
    }
}
