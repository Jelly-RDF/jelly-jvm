package eu.neverblink.jelly.core.patch.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.patch.PatchEncoder;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.patch.*;

/**
 * Implementation of PatchEncoder.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@ExperimentalApi
@InternalApi
public class PatchEncoderImpl<TNode> extends PatchEncoder<TNode> {

    private boolean hasEmittedOptions = false;

    // These rows are always identical, so we can use singletons
    private static final RdfPatchRow ROW_PUNCTUATION = RdfPatchRow.newInstance()
        .setPunctuation(RdfPatchPunctuation.EMPTY);
    private static final RdfPatchRow ROW_TX_START = RdfPatchRow.newInstance()
        .setTransactionStart(RdfPatchTransactionStart.EMPTY);
    private static final RdfPatchRow ROW_TX_COMMIT = RdfPatchRow.newInstance()
        .setTransactionCommit(RdfPatchTransactionCommit.EMPTY);
    private static final RdfPatchRow ROW_TX_ABORT = RdfPatchRow.newInstance()
        .setTransactionAbort(RdfPatchTransactionAbort.EMPTY);

    static {
        // Pre-calculate the serialized sizes of the static rows to avoid repeated calculations
        ROW_PUNCTUATION.getSerializedSize();
        ROW_TX_START.getSerializedSize();
        ROW_TX_COMMIT.getSerializedSize();
        ROW_TX_ABORT.getSerializedSize();
    }

    /**
     * Constructor.
     *
     * @param converter the converter to use
     * @param params    parameters for the encoder
     */
    public PatchEncoderImpl(ProtoEncoderConverter<TNode> converter, PatchEncoder.Params params) {
        super(converter, params);
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        rowBuffer.appendMessage().setName(nameEntry).getSerializedSize();
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        rowBuffer.appendMessage().setPrefix(prefixEntry).getSerializedSize();
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.appendMessage().setDatatype(datatypeEntry).getSerializedSize();
    }

    @Override
    public void addQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        rowBuffer.appendMessage().setStatementAdd(quad).getSerializedSize();
    }

    @Override
    public void deleteQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        rowBuffer.appendMessage().setStatementDelete(quad).getSerializedSize();
    }

    @Override
    public void addTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        rowBuffer.appendMessage().setStatementAdd(triple).getSerializedSize();
    }

    @Override
    public void deleteTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        rowBuffer.appendMessage().setStatementDelete(triple).getSerializedSize();
    }

    @Override
    public void transactionStart() {
        emitOptions();
        // TODO: replace with .appendMessage() in when we introduce RowBuffer fully
        rowBuffer.add(ROW_TX_START);
    }

    @Override
    public void transactionCommit() {
        emitOptions();
        // TODO: replace with .appendMessage() in when we introduce RowBuffer fully
        rowBuffer.add(ROW_TX_COMMIT);
    }

    @Override
    public void transactionAbort() {
        emitOptions();
        // TODO: replace with .appendMessage() in when we introduce RowBuffer fully
        rowBuffer.add(ROW_TX_ABORT);
    }

    @Override
    public void addNamespace(String name, TNode iriValue, TNode graph) {
        final var namespace = encodeNamespace(name, iriValue, graph);
        rowBuffer.appendMessage().setNamespaceAdd(namespace).getSerializedSize();
    }

    @Override
    public void deleteNamespace(String name, TNode iriValue, TNode graph) {
        final var namespace = encodeNamespace(name, iriValue, graph);
        rowBuffer.appendMessage().setNamespaceDelete(namespace).getSerializedSize();
    }

    private RdfPatchNamespace encodeNamespace(String name, TNode iriValue, TNode graph) {
        emitOptions();
        final var namespace = RdfPatchNamespace.newInstance().setName(name);
        this.currentNsBase = namespace;
        if (iriValue != null) {
            this.currentTerm = SpoTerm.NAMESPACE;
            converter.nodeToProto(getNodeEncoder(), iriValue);
        }
        if (graph != null) {
            this.currentGraphBase = namespace;
            this.currentTerm = SpoTerm.GRAPH;
            this.graphNodeToProtoWrapped(graph);
        }
        return namespace;
    }

    @Override
    public void header(String key, TNode value) {
        emitOptions();
        final var header = RdfPatchHeader.newInstance().setKey(key);
        this.currentHeaderBase = header;
        this.currentTerm = SpoTerm.HEADER;
        converter.nodeToProto(getNodeEncoder(), value);
        rowBuffer.appendMessage().setHeader(header).getSerializedSize();
    }

    @Override
    public void punctuation() {
        emitOptions();
        if (options.getStreamType() != PatchStreamType.PUNCTUATED) {
            throw new RdfProtoSerializationError("Punctuation is not allowed in this stream type.");
        }
        // TODO: replace with .appendMessage() in when we introduce RowBuffer fully
        rowBuffer.add(ROW_PUNCTUATION);
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.appendMessage().setOptions(options).getSerializedSize();
    }
}
