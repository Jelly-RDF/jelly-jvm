package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.ProtoEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.RdfTerm;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import java.util.Collection;

/**
 * Stateful encoder of a protobuf RDF stream.
 * <p>
 * This class supports all stream types and options, but usually does not check if the user is conforming to them.
 * It will, for example, allow the user to send generalized triples in a stream that should not have them.
 * Take care to ensure the correctness of the transmitted data, or use the specialized wrappers from the stream package.
 */
public class ProtoEncoderImpl<TNode> extends ProtoEncoder<TNode> {

    private boolean hasEmittedOptions = false;
    private final Collection<RdfStreamRow> rowBuffer;

    /**
     * Constructor for the ProtoEncoderImpl class.
     * <p>
     *
     * @param converter converter for the encoder
     * @param params    parameters object for the encoder
     */
    public ProtoEncoderImpl(ProtoEncoderConverter<TNode> converter, ProtoEncoder.Params params) {
        super(converter, params);
        this.rowBuffer = appendableRowBuffer;
    }

    @Override
    public void handleTriple(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleToProto(subject, predicate, object);
        final var mainRow = RdfStreamRow.newBuilder().setTriple(triple.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfStreamRow.newBuilder().setQuad(quad.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void handleGraphStart(TNode graph) {
        emitOptions();
        final var graphNode = converter.graphNodeToProto(nodeEncoder.provide(), graph);
        final var graphStart = new RdfTerm.GraphStart(graphNode);
        final var graphRow = RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        rowBuffer.add(graphRow);
    }

    @Override
    public void handleGraphEnd() {
        if (!hasEmittedOptions) {
            throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one");
        }

        final var graphEnd = new RdfTerm.GraphEnd();
        final var graphRow = RdfStreamRow.newBuilder().setGraphEnd(graphEnd.toProto()).build();
        rowBuffer.add(graphRow);
    }

    @Override
    public void handleNamespace(String prefix, TNode namespace) {
        if (!enableNamespaceDeclarations) {
            throw new RdfProtoSerializationError("Namespace declarations are not enabled in this stream");
        }

        emitOptions();

        final var namespaceTerm = converter.nodeToProto(nodeEncoder.provide(), namespace);
        if (!(namespaceTerm instanceof RdfTerm.Iri iriTerm)) {
            throw new RdfProtoSerializationError("Namespace must be an IRI");
        }

        final var mainRow = RdfStreamRow.newBuilder()
            .setNamespace(RdfNamespaceDeclaration.newBuilder().setName(prefix).setValue(iriTerm.toProto()).build())
            .build();

        rowBuffer.add(mainRow);
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        rowBuffer.add(RdfStreamRow.newBuilder().setName(nameEntry).build());
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        rowBuffer.add(RdfStreamRow.newBuilder().setPrefix(prefixEntry).build());
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.add(RdfStreamRow.newBuilder().setDatatype(datatypeEntry).build());
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.add(RdfStreamRow.newBuilder().setOptions(options).build());
    }
}
