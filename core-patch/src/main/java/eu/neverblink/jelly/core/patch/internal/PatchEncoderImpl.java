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
        final var row = RdfPatchRow.newInstance().setName(nameEntry);
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        row.getSerializedSize();
        rowBuffer.add(row);
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        final var row = RdfPatchRow.newInstance().setPrefix(prefixEntry);
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        row.getSerializedSize();
        rowBuffer.add(row);
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        final var row = RdfPatchRow.newInstance().setDatatype(datatypeEntry);
        row.getSerializedSize();
        rowBuffer.add(row);
    }

    @Override
    public void addQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfPatchRow.newInstance().setStatementAdd(quad);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfPatchRow.newInstance().setStatementDelete(quad);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
    }

    @Override
    public void addTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        final var mainRow = RdfPatchRow.newInstance().setStatementAdd(triple);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        final var mainRow = RdfPatchRow.newInstance().setStatementDelete(triple);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
    }

    @Override
    public void transactionStart() {
        emitOptions();
        rowBuffer.add(ROW_TX_START);
    }

    @Override
    public void transactionCommit() {
        emitOptions();
        rowBuffer.add(ROW_TX_COMMIT);
    }

    @Override
    public void transactionAbort() {
        emitOptions();
        rowBuffer.add(ROW_TX_ABORT);
    }

    @Override
    public void addNamespace(String name, TNode iriValue, TNode graph) {
        final var namespace = encodeNamespace(name, iriValue, graph);
        final var mainRow = RdfPatchRow.newInstance().setNamespaceAdd(namespace);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteNamespace(String name, TNode iriValue, TNode graph) {
        final var namespace = encodeNamespace(name, iriValue, graph);
        final var mainRow = RdfPatchRow.newInstance().setNamespaceDelete(namespace);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
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
        final var mainRow = RdfPatchRow.newInstance().setHeader(header);
        mainRow.getSerializedSize();
        rowBuffer.add(mainRow);
    }

    @Override
    public void punctuation() {
        emitOptions();
        if (options.getStreamType() != PatchStreamType.PUNCTUATED) {
            throw new RdfProtoSerializationError("Punctuation is not allowed in this stream type.");
        }
        rowBuffer.add(ROW_PUNCTUATION);
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        final var row = RdfPatchRow.newInstance().setOptions(options);
        row.getSerializedSize();
        rowBuffer.add(row);
    }
}
