package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProtoEncoderImpl<TNode, TTriple, TQuad> extends ProtoEncoder<TNode, TTriple, TQuad> {

    private boolean hasEmittedOptions = false;
    private final List<RdfStreamRow> rowBuffer;

    protected ProtoEncoderImpl(
        NodeEncoder<TNode> nodeEncoder,
        ProtoEncoderConverter<TNode, TTriple, TQuad> converter,
        ProtoEncoder.Params params
    ) {
        super(nodeEncoder, converter, params);
        this.rowBuffer = Optional.ofNullable(appendableRowBuffer).orElse(new ArrayList<>());
    }

    @Override
    public Iterable<RdfStreamRow> addTripleStatement(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        var triple = tripleToProto(subject, predicate, object);
        var mainRow = RdfStreamRow.newBuilder().setTriple(triple.toProto()).build();
        return appendAndReturn(mainRow);
    }

    @Override
    public Iterable<RdfStreamRow> addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        var quad = quadToProto(subject, predicate, object, graph);
        var mainRow = RdfStreamRow.newBuilder().setQuad(quad.toProto()).build();
        return appendAndReturn(mainRow);
    }

    @Override
    public Iterable<RdfStreamRow> startGraph(TNode graph) {
        emitOptions();
        var graphNode = converter.graphNodeToProto(nodeEncoder, graph);
        var graphStart = new RdfTerm.GraphStart(graphNode);
        var graphRow = RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        return appendAndReturn(graphRow);
    }

    @Override
    public Iterable<RdfStreamRow> startDefaultGraph() {
        emitOptions();
        var defaultGraph = new RdfTerm.DefaultGraph();
        var graphStart = new RdfTerm.GraphStart(defaultGraph);
        var graphRow = RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        return appendAndReturn(graphRow);
    }

    @Override
    public Iterable<RdfStreamRow> endGraph() {
        var graphEnd = new RdfTerm.GraphEnd();
        var graphRow = RdfStreamRow.newBuilder().setGraphEnd(graphEnd.toProto()).build();
        return appendAndReturn(graphRow);
    }

    @Override
    public Iterable<RdfStreamRow> declareNamespace(String name, String iriValue) {
        if (!enableNamespaceDeclarations) {
            throw new JellyException.RdfProtoSerializationError(
                "Namespace declarations are not enabled in this stream"
            );
        }

        emitOptions();
        var iri = nodeEncoder.makeIri(iriValue);
        var mainRow = RdfStreamRow.newBuilder()
            .setNamespace(RdfNamespaceDeclaration.newBuilder().setName(name).setValue(iri.toProto()).build())
            .build();

        return appendAndReturn(mainRow);
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

    private Iterable<RdfStreamRow> appendAndReturn(RdfStreamRow row) {
        rowBuffer.add(row);
        if (hasEmittedOptions) {
            var list = new ArrayList<>(rowBuffer);
            rowBuffer.clear();
            return list;
        } else {
            return List.of();
        }
    }

    private void emitOptions() {
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        rowBuffer.add(RdfStreamRow.newBuilder().setOptions(options).build());
    }
}
