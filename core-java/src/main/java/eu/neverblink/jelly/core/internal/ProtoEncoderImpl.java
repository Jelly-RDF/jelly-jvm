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
    private final Collection<RdfStreamRow> rowBuffer;
    
    // Rows ending the graph are always identical
    private static final RdfStreamRow ROW_GRAPH_END = 
        RdfStreamRow.newInstance().setGraphEnd(RdfGraphEnd.EMPTY);

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
        final var mainRow = RdfStreamRow.newInstance().setTriple(triple);
        rowBuffer.add(mainRow);
    }

    @Override
    public void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfStreamRow.newInstance().setQuad(quad);
        rowBuffer.add(mainRow);
    }

    @Override
    public void handleGraphStart(TNode graph) {
        emitOptions();
        final var graphStart = graphStartToProto(graph);
        final var graphRow = RdfStreamRow.newInstance().setGraphStart(graphStart);
        rowBuffer.add(graphRow);
    }

    @Override
    public void handleGraphEnd() {
        if (!hasEmittedOptions) {
            throw new RdfProtoSerializationError("Cannot end a delimited graph before starting one");
        }
        rowBuffer.add(ROW_GRAPH_END);
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
        final var mainRow = RdfStreamRow.newInstance().setNamespace(ns);
        rowBuffer.add(mainRow);
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        rowBuffer.add(RdfStreamRow.newInstance().setName(nameEntry));
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        rowBuffer.add(RdfStreamRow.newInstance().setPrefix(prefixEntry));
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        rowBuffer.add(RdfStreamRow.newInstance().setDatatype(datatypeEntry));
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.add(RdfStreamRow.newInstance().setOptions(options));
    }
}
