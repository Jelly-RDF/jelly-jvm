package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.Collection;

public class ProtoEncoderImpl<TNode, TTriple, TQuad> extends ProtoEncoder<TNode, TTriple, TQuad> {

    private boolean hasEmittedOptions = false;
    private final Collection<RdfStreamRow> rowBuffer;

    protected ProtoEncoderImpl(
        NodeEncoder<TNode> nodeEncoder,
        ProtoEncoderConverter<TNode, TTriple, TQuad> converter,
        ProtoEncoder.Params params
    ) {
        super(nodeEncoder, converter, params);
        this.rowBuffer = appendableRowBuffer;
    }

    @Override
    public void addTripleStatement(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        final var triple = tripleToProto(subject, predicate, object);
        final var mainRow = RdfStreamRow.newBuilder().setTriple(triple.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        final var quad = quadToProto(subject, predicate, object, graph);
        final var mainRow = RdfStreamRow.newBuilder().setQuad(quad.toProto()).build();
        rowBuffer.add(mainRow);
    }

    @Override
    public void startGraph(TNode graph) {
        emitOptions();
        final var graphNode = converter.graphNodeToProto(nodeEncoder, graph);
        final var graphStart = new RdfTerm.GraphStart(graphNode);
        final var graphRow = RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        rowBuffer.add(graphRow);
    }

    @Override
    public void startDefaultGraph() {
        emitOptions();
        final var defaultGraph = new RdfTerm.DefaultGraph();
        final var graphStart = new RdfTerm.GraphStart(defaultGraph);
        final var graphRow = RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        rowBuffer.add(graphRow);
    }

    @Override
    public void endGraph() {
        final var graphEnd = new RdfTerm.GraphEnd();
        final var graphRow = RdfStreamRow.newBuilder().setGraphEnd(graphEnd.toProto()).build();
        rowBuffer.add(graphRow);
    }

    @Override
    public void declareNamespace(String name, String iriValue) {
        if (!enableNamespaceDeclarations) {
            throw new RdfProtoSerializationError("Namespace declarations are not enabled in this stream");
        }

        emitOptions();
        final var iri = nodeEncoder.makeIri(iriValue);
        final var mainRow = RdfStreamRow.newBuilder()
            .setNamespace(RdfNamespaceDeclaration.newBuilder().setName(name).setValue(iri.toProto()).build())
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
