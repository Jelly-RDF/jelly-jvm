package eu.neverblink.jelly.core.patch.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.patch.PatchEncoder;
import eu.neverblink.jelly.core.proto.v1.patch.*;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;

/**
 * Implementation of PatchEncoder.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@ExperimentalApi
@InternalApi
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
        // TODO: optimize, use a singleton here
        final var transactionStart = RdfPatchTransactionStart.newInstance();
        final var mainRow = RdfPatchRow.newInstance().setTransactionStart(transactionStart);
        rowBuffer.add(mainRow);
    }

    @Override
    public void transactionCommit() {
        emitOptions();
        // TODO: optimize, use a singleton here
        final var transactionCommit = RdfPatchTransactionCommit.newInstance();
        final var mainRow = RdfPatchRow.newInstance().setTransactionCommit(transactionCommit);
        rowBuffer.add(mainRow);
    }

    @Override
    public void transactionAbort() {
        emitOptions();
        // TODO: optimize, use a singleton here
        final var transactionAbort = RdfPatchTransactionAbort.newInstance();
        final var mainRow = RdfPatchRow.newInstance().setTransactionAbort(transactionAbort);
        rowBuffer.add(mainRow);
    }

    @Override
    public void addNamespace(String name, TNode iriValue, TNode graph) {
        emitOptions();
        // TODO: fix
//        final var namespace = converter.nodeToProto(nodeEncoder.provide(), iriValue);
//        if (!(namespace instanceof RdfTerm.Iri valueIri)) {
//            throw new RdfProtoSerializationError("Namespace must be an IRI");
//        }
//
//        final var graphIri = encodeNsIri(graph);
//
//        final var namespaceEntryBuilder = RdfPatchNamespace.newInstance().setName(name).setValue(valueIri);
//
//        if (graphIri != null) {
//            namespaceEntryBuilder.setGraph(graphIri);
//        }
//
//        final var namespaceEntry = namespaceEntryBuilder;
//        final var mainRow = RdfPatchRow.newInstance().setNamespaceAdd(namespaceEntry);
//        rowBuffer.add(mainRow);
    }

    @Override
    public void deleteNamespace(String name, TNode iriValue, TNode graph) {
        emitOptions();

        final var valueIri = encodeNsIri(iriValue);
        final var graphIri = encodeNsIri(graph);

        final var namespaceEntryBuilder = RdfPatchNamespace.newInstance().setName(name);

        if (valueIri != null) {
            namespaceEntryBuilder.setValue(valueIri);
        }

        if (graphIri != null) {
            namespaceEntryBuilder.setGraph(graphIri);
        }

        final var namespaceEntry = namespaceEntryBuilder;
        final var mainRow = RdfPatchRow.newInstance().setNamespaceDelete(namespaceEntry);
        rowBuffer.add(mainRow);
    }

    @Override
    public void header(String key, TNode value) {
        emitOptions();
        // TODO: fix
//        var valueTerm = converter.nodeToProto(nodeEncoder.provide(), value);
//        var headerBuilder = RdfPatchHeader.newInstance().setKey(key);
//
//        if (valueTerm instanceof RdfTerm.Iri valueIri) {
//            headerBuilder.setHIri(valueIri);
//        } else if (valueTerm instanceof RdfTerm.BNode valueBlankNode) {
//            headerBuilder.setHBnode(valueBlankNode);
//        } else if (valueTerm instanceof RdfTerm.LiteralTerm valueLiteral) {
//            headerBuilder.setHLiteral(valueLiteral);
//        } else if (valueTerm instanceof RdfTerm.Triple valueTriple) {
//            headerBuilder.setHTripleTerm(valueTriple);
//        } else {
//            throw new RdfProtoSerializationError("Header value must be an IRI, literal, or blank node");
//        }
//
//        var headerEntry = headerBuilder;
//        var mainRow = RdfPatchRow.newInstance().setHeader(headerEntry);
//        rowBuffer.add(mainRow);
    }

    @Override
    public void punctuation() {
        emitOptions();
        if (options.getStreamType() != PatchStreamType.PUNCTUATED) {
            throw new RdfProtoSerializationError("Punctuation is not allowed in this stream type.");
        }

        // TODO: optimize, use a singleton here
        var punctuation = RdfPatchPunctuation.newInstance();
        var mainRow = RdfPatchRow.newInstance().setPunctuation(punctuation);
        rowBuffer.add(mainRow);
    }

    private RdfIri encodeNsIri(TNode iri) {
        // TODO: fix
        return null;
//        if (iri == null) {
//            return null;
//        }
//
//        final var term = converter.nodeToProto(nodeEncoder.provide(), iri);
//        if (!(term instanceof RdfTerm.Iri iriTerm)) {
//            throw new RdfProtoSerializationError("Namespace must be an IRI");
//        }
//        return iriTerm;
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.add(RdfPatchRow.newInstance().setOptions(options));
    }
}
