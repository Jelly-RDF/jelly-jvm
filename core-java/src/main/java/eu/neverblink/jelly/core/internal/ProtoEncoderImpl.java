package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.*;
import java.util.Collection;

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
        rowBuffer.appendRow().setTriple(triple).getSerializedSize();
    }

    @Override
    public void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        // Calculate the size of the row now, as all objects are likely still in L1/L2 cache.
        rowBuffer.appendRow().setQuad(quad).getSerializedSize();
    }

    @Override
    public void handleGraphStart(TNode graph) {
        emitOptions();
        final var graphStart = graphStartToProto(graph);
        rowBuffer.appendRow().setGraphStart(graphStart).getSerializedSize();
    }

    @Override
    public void handleGraphEnd() {
        if (!hasEmittedOptions) {
            throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one");
        }
        rowBuffer.appendRow().setGraphEnd(RdfGraphEnd.EMPTY);
    }

    @Override
    public void handleNamespace(String prefix, TNode namespace) {
        if (!enableNamespaceDeclarations) {
            throw new RdfProtoSerializationError("Namespace declarations are not enabled in this stream");
        }

        emitOptions();

        final var ns = RdfNamespaceDeclaration.newInstance().setName(prefix);
        this.currentNsBase = ns;
        this.currentTerm = SpoTerm.NAMESPACE;
        converter.nodeToProto(nodeEncoder.provide(), namespace);
        rowBuffer.appendRow().setNamespace(ns);
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        rowBuffer.appendRow().setName(nameEntry).getSerializedSize();
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        rowBuffer.appendRow().setPrefix(prefixEntry).getSerializedSize();
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.appendRow().setDatatype(datatypeEntry).getSerializedSize();
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.appendRow().setOptions(options);
    }
}
