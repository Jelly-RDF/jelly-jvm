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
        rowBuffer.add(RdfPatchRow.newInstance().setName(nameEntry));
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        rowBuffer.add(RdfPatchRow.newInstance().setPrefix(prefixEntry));
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.add(RdfPatchRow.newInstance().setDatatype(datatypeEntry));
    }

    @Override
    public void addQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfPatchRow.newInstance().setStatementAdd(quad);
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfPatchRow.newInstance().setStatementDelete(quad);
        rowBuffer.add(mainRow);
    }

    @Override
    public void addTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        final var mainRow = RdfPatchRow.newInstance().setStatementAdd(triple);
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        final var mainRow = RdfPatchRow.newInstance().setStatementDelete(triple);
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
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteNamespace(String name, TNode iriValue, TNode graph) {
        final var namespace = encodeNamespace(name, iriValue, graph);
        final var mainRow = RdfPatchRow.newInstance().setNamespaceDelete(namespace);
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
        var mainRow = RdfPatchRow.newInstance().setHeader(header);
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
        rowBuffer.add(RdfPatchRow.newInstance().setOptions(options));
    }
}
