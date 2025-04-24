package eu.neverblink.jelly.core.patch.internal;

import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.RdfTerm;
import eu.neverblink.jelly.core.patch.PatchEncoder;
import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Implementation of PatchEncoder.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
public class PatchEncoderImpl<TNode> extends PatchEncoder<TNode> {

    private boolean hasEmittedOptions = false;

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
        rowBuffer.add(RdfPatchRow.newBuilder().setName(nameEntry).build());
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        rowBuffer.add(RdfPatchRow.newBuilder().setPrefix(prefixEntry).build());
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.add(RdfPatchRow.newBuilder().setDatatype(datatypeEntry).build());
    }

    @Override
    public void addQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfPatchRow.newBuilder().setStatementAdd(quad.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfPatchRow.newBuilder().setStatementDelete(quad.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void addTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        final var mainRow = RdfPatchRow.newBuilder().setStatementAdd(triple.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleInQuadToProto(subject, predicate, object);
        final var mainRow = RdfPatchRow.newBuilder().setStatementDelete(triple.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void transactionStart() {
        emitOptions();
        final var transactionStart = RdfPatchTransactionStart.getDefaultInstance();
        final var mainRow = RdfPatchRow.newBuilder().setTransactionStart(transactionStart).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void transactionCommit() {
        emitOptions();
        final var transactionCommit = RdfPatchTransactionCommit.getDefaultInstance();
        final var mainRow = RdfPatchRow.newBuilder().setTransactionCommit(transactionCommit).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void transactionAbort() {
        emitOptions();
        final var transactionAbort = RdfPatchTransactionAbort.getDefaultInstance();
        final var mainRow = RdfPatchRow.newBuilder().setTransactionAbort(transactionAbort).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void addNamespace(String name, TNode iriValue, TNode graph) {
        emitOptions();
        final var namespace = converter.nodeToProto(nodeEncoder.provide(), iriValue);
        if (!(namespace instanceof RdfTerm.Iri valueIri)) {
            throw new RdfProtoSerializationError("Namespace must be an IRI");
        }

        final var graphIri = encodeNsIri(graph);

        final var namespaceEntryBuilder = RdfPatchNamespace.newBuilder().setName(name).setValue(valueIri.toProto());

        if (graphIri != null) {
            namespaceEntryBuilder.setGraph(graphIri.toProto());
        }

        final var namespaceEntry = namespaceEntryBuilder.build();
        final var mainRow = RdfPatchRow.newBuilder().setNamespaceAdd(namespaceEntry).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteNamespace(String name, TNode iriValue, TNode graph) {
        emitOptions();

        final var valueIri = encodeNsIri(iriValue);
        final var graphIri = encodeNsIri(graph);

        final var namespaceEntryBuilder = RdfPatchNamespace.newBuilder().setName(name);

        if (valueIri != null) {
            namespaceEntryBuilder.setValue(valueIri.toProto());
        }

        if (graphIri != null) {
            namespaceEntryBuilder.setGraph(graphIri.toProto());
        }

        final var namespaceEntry = namespaceEntryBuilder.build();
        final var mainRow = RdfPatchRow.newBuilder().setNamespaceDelete(namespaceEntry).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void header(String key, TNode value) {
        emitOptions();
        var valueTerm = converter.nodeToProto(nodeEncoder.provide(), value);
        var headerBuilder = RdfPatchHeader.newBuilder().setKey(key);

        if (valueTerm instanceof RdfTerm.Iri valueIri) {
            headerBuilder.setHIri(valueIri.toProto());
        } else if (valueTerm instanceof RdfTerm.BNode valueBlankNode) {
            headerBuilder.setHBnode(valueBlankNode.toProto());
        } else if (valueTerm instanceof RdfTerm.LiteralTerm valueLiteral) {
            headerBuilder.setHLiteral(valueLiteral.toProto());
        } else if (valueTerm instanceof RdfTerm.Triple valueTriple) {
            headerBuilder.setHTripleTerm(valueTriple.toProto());
        } else {
            throw new RdfProtoSerializationError("Header value must be an IRI, literal, or blank node");
        }

        var headerEntry = headerBuilder.build();
        var mainRow = RdfPatchRow.newBuilder().setHeader(headerEntry).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void punctuation() {
        emitOptions();
        if (options.getStreamType() != PatchStreamType.PATCH_STREAM_TYPE_PUNCTUATED) {
            throw new RdfProtoSerializationError("Punctuation is not allowed in this stream type.");
        }

        var punctuation = RdfPatchPunctuation.getDefaultInstance();
        var mainRow = RdfPatchRow.newBuilder().setPunctuation(punctuation).build();
        rowBuffer.add(mainRow);
    }

    private RdfTerm.Iri encodeNsIri(TNode iri) {
        if (iri == null) {
            return null;
        }

        final var term = converter.nodeToProto(nodeEncoder.provide(), iri);
        if (!(term instanceof RdfTerm.Iri iriTerm)) {
            throw new RdfProtoSerializationError("Namespace must be an IRI");
        }
        return iriTerm;
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.add(RdfPatchRow.newBuilder().setOptions(options).build());
    }
}
