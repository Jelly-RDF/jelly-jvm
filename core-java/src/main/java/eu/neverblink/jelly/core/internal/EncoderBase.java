package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.internal.proto.*;
import eu.neverblink.jelly.core.internal.utils.LazyProperty;
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
    protected final LazyProperty<NodeEncoder<TNode>> nodeEncoder;

    protected final LastNodeHolder<TNode> lastSubject = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastPredicate = new LastNodeHolder<>();
    protected final LastNodeHolder<TNode> lastObject = new LastNodeHolder<>();
    protected TNode lastGraph = null;

    protected SpoTerm currentTerm = SpoTerm.SUBJECT;
    private SpoBase.Setters currentSpoBase = null;
    protected GraphBase.Setters currentGraphBase = null;
    protected NsBase.Setters currentNsBase = null;
    protected HeaderBase.Setters currentHeaderBase = null;

    protected EncoderBase(ProtoEncoderConverter<TNode> converter) {
        this.converter = converter;
        this.nodeEncoder = new LazyProperty<>(() ->
            NodeEncoderImpl.create(this, getPrefixTableSize(), getNameTableSize(), getDatatypeTableSize())
        );
    }

    protected abstract int getNameTableSize();

    protected abstract int getPrefixTableSize();

    protected abstract int getDatatypeTableSize();

    protected final RdfTriple tripleToProto(TNode subject, TNode predicate, TNode object) {
        final RdfTriple.Mutable triple = RdfTriple.newInstance();
        this.currentSpoBase = triple;
        nodeToProtoWrapped(subject, lastSubject, SpoTerm.SUBJECT);
        nodeToProtoWrapped(predicate, lastPredicate, SpoTerm.PREDICATE);
        nodeToProtoWrapped(object, lastObject, SpoTerm.OBJECT);
        return triple;
    }

    protected final RdfQuad quadToProto(TNode subject, TNode predicate, TNode object, TNode graph) {
        final RdfQuad.Mutable quad = RdfQuad.newInstance();
        this.currentSpoBase = quad;
        this.currentGraphBase = quad;
        nodeToProtoWrapped(subject, lastSubject, SpoTerm.SUBJECT);
        nodeToProtoWrapped(predicate, lastPredicate, SpoTerm.PREDICATE);
        nodeToProtoWrapped(object, lastObject, SpoTerm.OBJECT);
        graphNodeToProtoWrapped(graph);
        return quad;
    }

    /**
     * Converts a triple to an RdfQuad object with a null graph.
     * <p>
     * Used in RDF-Patch for triple add/delete operations.
     */
    protected final RdfQuad tripleInQuadToProto(TNode subject, TNode predicate, TNode object) {
        final RdfQuad.Mutable quad = RdfQuad.newInstance();
        this.currentSpoBase = quad;
        nodeToProtoWrapped(subject, lastSubject, SpoTerm.SUBJECT);
        nodeToProtoWrapped(predicate, lastPredicate, SpoTerm.PREDICATE);
        nodeToProtoWrapped(object, lastObject, SpoTerm.OBJECT);
        return quad;
    }

    /**
     * Converts a graph term to an RdfGraphStart object.
     */
    protected final RdfGraphStart graphStartToProto(TNode graph) {
        final RdfGraphStart.Mutable graphStart = RdfGraphStart.newInstance();
        this.currentGraphBase = graphStart;
        currentTerm = SpoTerm.GRAPH;
        converter.graphNodeToProto(nodeEncoder.provide(), graph);
        return graphStart;
    }

    private void nodeToProtoWrapped(TNode node, LastNodeHolder<TNode> lastNodeHolder, SpoTerm term) {
        if (!node.equals(lastNodeHolder.get())) {
            lastNodeHolder.set(node);
            currentTerm = term;
            converter.nodeToProto(nodeEncoder.provide(), node);
        }
    }

    protected final void graphNodeToProtoWrapped(TNode node) {
        // Graph nodes may be null in Jena for example... so we need to handle that.
        if ((node != null || lastGraph != null) && (node == null || !node.equals(lastGraph))) {
            lastGraph = node;
            currentTerm = SpoTerm.GRAPH;
            converter.graphNodeToProto(nodeEncoder.provide(), node);
        }
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
        currentTerm = SpoTerm.SUBJECT;
        converter.nodeToProto(nodeEncoder.provide(), subject);
        currentTerm = SpoTerm.PREDICATE;
        converter.nodeToProto(nodeEncoder.provide(), predicate);
        currentTerm = SpoTerm.OBJECT;
        converter.nodeToProto(nodeEncoder.provide(), object);
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
        // TODO: use a shared instance here
        currentGraphBase.setGDefaultGraph(RdfDefaultGraph.newInstance());
    }
}
