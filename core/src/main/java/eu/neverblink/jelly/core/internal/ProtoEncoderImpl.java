package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Stateful encoder of a protobuf RDF stream.
 * <p>
 * This class supports all stream types and options, but usually does not check if the user is conforming to them.
 * It will, for example, allow the user to send generalized triples in a stream that should not have them.
 * Take care to ensure the correctness of the transmitted data, or use the specialized wrappers from the stream package.
 */
@InternalApi
public class ProtoEncoderImpl<TNode> extends ProtoEncoder<TNode> {

    private boolean hasEmittedOptions = false;

    /**
     * Constructor for the ProtoEncoderImpl class.
     * <p>
     *
     * @param converter converter for the encoder
     * @param params    parameters object for the encoder
     */
    public ProtoEncoderImpl(ProtoEncoderConverter<TNode> converter, ProtoEncoder.Params params) {
        super(converter, params);
    }

    @Override
    public void handleTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleToProto(subject, predicate, object);
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        rowBuffer.appendMessage().setTriple(triple).getSerializedSize();
    }

    @Override
    public void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        rowBuffer.appendMessage().setQuad(quad).getSerializedSize();
    }

    @Override
    public void handleGraphStart(TNode graph) {
        emitOptions();
        final var graphStart = graphStartToProto(graph);
        rowBuffer.appendMessage().setGraphStart(graphStart).getSerializedSize();
    }

    @Override
    public void handleGraphEnd() {
        if (!hasEmittedOptions) {
            throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one");
        }
        rowBuffer.appendMessage().setGraphEnd(RdfGraphEnd.EMPTY).getSerializedSize();
    }

    @Override
    public void handleNamespace(String prefix, TNode namespace) {
        if (!enableNamespaceDeclarations) {
            throw new RdfProtoSerializationError("Namespace declarations are not enabled in this stream");
        }

        emitOptions();

        final var ns = RdfNamespaceDeclaration.newInstance().setName(prefix);
        final var encoded = converter.nodeToProto(getNodeEncoder(), namespace);
        ns.setValue((RdfIri) encoded.node());
        rowBuffer.appendMessage().setNamespace(ns).getSerializedSize();
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        rowBuffer.appendMessage().setName(nameEntry).getSerializedSize();
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        rowBuffer.appendMessage().setPrefix(prefixEntry).getSerializedSize();
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.appendMessage().setDatatype(datatypeEntry).getSerializedSize();
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.appendMessage().setOptions(options).getSerializedSize();
    }
}
