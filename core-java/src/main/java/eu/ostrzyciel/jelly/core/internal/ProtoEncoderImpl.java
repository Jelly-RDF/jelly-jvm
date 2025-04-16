package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.Rdf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProtoEncoderImpl<TNode, TTriple, TQuad> extends ProtoEncoder<TNode, TTriple, TQuad> {

    private boolean hasEmittedOptions = false;
    private final List<Rdf.RdfStreamRow> rowBuffer;

    protected ProtoEncoderImpl(
        NodeEncoder<TNode> nodeEncoder,
        ProtoEncoderConverter<TNode, TTriple, TQuad> converter,
        ProtoEncoder.Params params
    ) {
        super(nodeEncoder, converter, params);
        this.rowBuffer = Optional.ofNullable(appendableRowBuffer).orElse(new ArrayList<>());
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> addTripleStatement(TNode subject, TNode predicate, TNode object) {
        emitOptions();
        var triple = tripleToProto(subject, predicate, object);
        var mainRow = Rdf.RdfStreamRow.newBuilder().setTriple(triple.toProto()).build();
        return appendAndReturn(mainRow);
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> addQuadStatement(TNode subject, TNode predicate, TNode object, TNode graph) {
        emitOptions();
        var quad = quadToProto(subject, predicate, object, graph);
        var mainRow = Rdf.RdfStreamRow.newBuilder().setQuad(quad.toProto()).build();
        return appendAndReturn(mainRow);
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> startGraph(TNode graph) {
        emitOptions();
        var graphNode = converter.graphNodeToProto(nodeEncoder, graph);
        var graphStart = new RdfTerm.GraphStart(graphNode);
        var graphRow = Rdf.RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        return appendAndReturn(graphRow);
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> startDefaultGraph() {
        emitOptions();
        var defaultGraph = new RdfTerm.DefaultGraph();
        var graphStart = new RdfTerm.GraphStart(defaultGraph);
        var graphRow = Rdf.RdfStreamRow.newBuilder().setGraphStart(graphStart.toProto()).build();
        return appendAndReturn(graphRow);
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> endGraph() {
        var graphEnd = new RdfTerm.GraphEnd();
        var graphRow = Rdf.RdfStreamRow.newBuilder().setGraphEnd(graphEnd.toProto()).build();
        return appendAndReturn(graphRow);
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> declareNamespace(String name, String iriValue) {
        if (!enableNamespaceDeclarations) {
            throw new JellyException.RdfProtoSerializationError(
                "Namespace declarations are not enabled in this stream"
            );
        }

        emitOptions();
        var iri = nodeEncoder.makeIri(iriValue);
        var mainRow = Rdf.RdfStreamRow.newBuilder()
            .setNamespace(Rdf.RdfNamespaceDeclaration.newBuilder().setName(name).setValue(iri.toProto()).build())
            .build();

        return appendAndReturn(mainRow);
    }

    @Override
    public void appendNameEntry(Rdf.RdfNameEntry nameEntry) {
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setName(nameEntry).build());
    }

    @Override
    public void appendPrefixEntry(Rdf.RdfPrefixEntry prefixEntry) {
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setPrefix(prefixEntry).build());
    }

    @Override
    public void appendDatatypeEntry(Rdf.RdfDatatypeEntry datatypeEntry) {
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setDatatype(datatypeEntry).build());
    }

    private Iterable<Rdf.RdfStreamRow> appendAndReturn(Rdf.RdfStreamRow row) {
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
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setOptions(options).build());
    }
}
