package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.Rdf;
import java.util.ArrayList;
import java.util.List;

public class ProtoTranscoderImpl implements ProtoTranscoder {

    private final Rdf.RdfStreamOptions supportedInputOptions;
    private final Rdf.RdfStreamOptions outputOptions;

    private final TranscoderLookup prefixLookup;
    private final TranscoderLookup nameLookup;
    private final TranscoderLookup datatypeLookup;

    private final List<Rdf.RdfStreamRow> rowBuffer = new ArrayList<>();

    private boolean inputUsesPrefixes = false;
    private boolean hasChangedTerms = false;
    private boolean hasEmittedOptions = false;

    public ProtoTranscoderImpl(Rdf.RdfStreamOptions supportedInputOptions, Rdf.RdfStreamOptions outputOptions) {
        this.supportedInputOptions = supportedInputOptions;
        this.outputOptions = outputOptions;
        prefixLookup = new TranscoderLookup(false, outputOptions.getMaxPrefixTableSize());
        nameLookup = new TranscoderLookup(true, outputOptions.getMaxNameTableSize());
        datatypeLookup = new TranscoderLookup(false, outputOptions.getMaxDatatypeTableSize());
    }

    @Override
    public Iterable<Rdf.RdfStreamRow> ingestRow(Rdf.RdfStreamRow row) {
        rowBuffer.clear();
        processRow(row);
        return rowBuffer;
    }

    @Override
    public Iterable<Rdf.RdfStreamFrame> ingestFrame(Rdf.RdfStreamFrame frame) {
        rowBuffer.clear();
        for (Rdf.RdfStreamRow row : frame.getRowsList()) {
            processRow(row);
        }
        var newFrame = Rdf.RdfStreamFrame.newBuilder()
            .addAllRows(rowBuffer)
            .putAllMetadata(frame.getMetadataMap())
            .build();
        rowBuffer.clear();
        return List.of(newFrame);
    }

    private void processRow(Rdf.RdfStreamRow row) {
        switch (row.getRowCase()) {
            case OPTIONS -> handleOptions(row.getOptions());
            case TRIPLE -> handleTriple(row);
            case QUAD -> handleQuad(row);
            case GRAPH_START -> handleGraphStart(row);
            case GRAPH_END -> handleIdentity(row);
            case NAMESPACE -> handleNamespaceDeclaration(row);
            case NAME -> handleName(row);
            case PREFIX -> handlePrefix(row);
            case DATATYPE -> handleDatatype(row);
            case ROW_NOT_SET -> throw new JellyException.RdfProtoTranscodingError("Row not set");
        }
    }

    private void handleName(Rdf.RdfStreamRow row) {
        var name = row.getName();
        var entry = nameLookup.addEntry(name.getId(), name.getValue());
        if (!entry.newEntry) {
            return;
        }

        if (entry.setId == name.getId()) {
            rowBuffer.add(row);
            return;
        }

        var newName = Rdf.RdfNameEntry.newBuilder().setId(entry.setId).setValue(name.getValue()).build();
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setName(newName).build());
    }

    private void handlePrefix(Rdf.RdfStreamRow row) {
        var prefix = row.getPrefix();
        var entry = prefixLookup.addEntry(prefix.getId(), prefix.getValue());
        if (!entry.newEntry) {
            return;
        }

        if (entry.setId == prefix.getId()) {
            rowBuffer.add(row);
            return;
        }

        var newPrefix = Rdf.RdfPrefixEntry.newBuilder().setId(entry.setId).setValue(prefix.getValue()).build();
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setPrefix(newPrefix).build());
    }

    private void handleDatatype(Rdf.RdfStreamRow row) {
        var datatype = row.getDatatype();
        var entry = datatypeLookup.addEntry(datatype.getId(), datatype.getValue());
        if (!entry.newEntry) {
            return;
        }

        if (entry.setId == datatype.getId()) {
            rowBuffer.add(row);
            return;
        }

        var newDatatype = Rdf.RdfDatatypeEntry.newBuilder().setId(entry.setId).setValue(datatype.getValue()).build();
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setDatatype(newDatatype).build());
    }

    private void handleIdentity(Rdf.RdfStreamRow row) {
        // No changes needed, just add the row to the buffer
        rowBuffer.add(row);
    }

    private void handleTriple(Rdf.RdfStreamRow row) {
        this.hasChangedTerms = false;
        var triple = RdfTerm.from(row.getTriple());

        var s1 = handleSpoTerm(triple.subject());
        var p1 = handleSpoTerm(triple.predicate());
        var o1 = handleSpoTerm(triple.object());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        var newTriple = new RdfTerm.Triple(s1, p1, o1);
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setTriple(newTriple.toProto()).build());
    }

    private void handleQuad(Rdf.RdfStreamRow row) {
        this.hasChangedTerms = false;
        var quad = RdfTerm.from(row.getQuad());

        var s1 = handleSpoTerm(quad.subject());
        var p1 = handleSpoTerm(quad.predicate());
        var o1 = handleSpoTerm(quad.object());
        var g1 = handleGraphTerm(quad.graph());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        var newQuad = new RdfTerm.Quad(s1, p1, o1, g1);
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setQuad(newQuad.toProto()).build());
    }

    private void handleGraphStart(Rdf.RdfStreamRow row) {
        this.hasChangedTerms = false;
        var graphStart = RdfTerm.from(row.getGraphStart());

        var g1 = handleGraphTerm(graphStart.graph());
        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        var newGraphStart = new RdfTerm.GraphStart(g1);
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setGraphStart(newGraphStart.toProto()).build());
    }

    private void handleNamespaceDeclaration(Rdf.RdfStreamRow row) {
        this.hasChangedTerms = false;
        var nsRow = row.getNamespace();
        var iriValue = handleIri(RdfTerm.from(nsRow.getValue()));

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        var namespace = Rdf.RdfNamespaceDeclaration.newBuilder()
            .setName(nsRow.getName())
            .setValue(iriValue.toProto())
            .build();

        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setNamespace(namespace).build());
    }

    private RdfTerm.SpoTerm handleSpoTerm(RdfTerm.SpoTerm term) {
        if (term instanceof RdfTerm.Iri iri) {
            return handleIri(iri);
        } else if (term instanceof RdfTerm.LiteralTerm literalTerm) {
            return handleLiteral(literalTerm);
        } else if (term instanceof RdfTerm.Triple triple) {
            return handleTripleTerm(triple);
        } else {
            return term;
        }
    }

    private RdfTerm.GraphTerm handleGraphTerm(RdfTerm.GraphTerm graph) {
        if (graph instanceof RdfTerm.Iri iri) {
            return handleIri(iri);
        } else if (graph instanceof RdfTerm.LiteralTerm literalTerm) {
            return handleLiteral(literalTerm);
        } else {
            return graph;
        }
    }

    private RdfTerm.Iri handleIri(RdfTerm.Iri iri) {
        var prefix = iri.prefixId();
        var name = iri.nameId();
        var prefix1 = inputUsesPrefixes ? prefixLookup.remap(prefix) : 0;
        var name1 = nameLookup.remap(name);
        if (prefix1 != prefix || name1 != name) {
            hasChangedTerms = true;
            return new RdfTerm.Iri(prefix1, name1);
        }
        return iri;
    }

    private RdfTerm.LiteralTerm handleLiteral(RdfTerm.LiteralTerm literal) {
        if (!(literal instanceof RdfTerm.DtLiteral dtLiteral)) {
            return literal;
        }

        var dt = dtLiteral.datatype();
        var dt1 = datatypeLookup.remap(dt);
        if (dt1 != dt) {
            hasChangedTerms = true;
            return new RdfTerm.DtLiteral(dtLiteral.lex(), dt1);
        }

        return literal;
    }

    private RdfTerm.Triple handleTripleTerm(RdfTerm.Triple triple) {
        var s1 = handleSpoTerm(triple.subject());
        var p1 = handleSpoTerm(triple.predicate());
        var o1 = handleSpoTerm(triple.object());
        if (!s1.equals(triple.subject()) || !p1.equals(triple.predicate()) || !o1.equals(triple.object())) {
            hasChangedTerms = true;
            return new RdfTerm.Triple(s1, p1, o1);
        }
        return triple;
    }

    private void handleOptions(Rdf.RdfStreamOptions options) {
        if (supportedInputOptions != null) {
            if (outputOptions.getPhysicalType() != options.getPhysicalType()) {
                throw new JellyException.RdfProtoDeserializationError(
                    "Input stream has a different physical type than the output. Input: %s output: %s".formatted(
                            options.getPhysicalType(),
                            outputOptions.getPhysicalType()
                        )
                );
            }
            JellyOptions.checkCompatibility(options, supportedInputOptions);
        }

        this.inputUsesPrefixes = options.getMaxPrefixTableSize() > 0;

        if (inputUsesPrefixes) {
            prefixLookup.newInputStream(options.getMaxPrefixTableSize());
        } else if (outputOptions.getMaxPrefixTableSize() > 0) {
            throw new JellyException.RdfProtoTranscodingError(
                "Output stream uses prefixes, but the input stream does not."
            );
        }

        nameLookup.newInputStream(options.getMaxNameTableSize());
        datatypeLookup.newInputStream(options.getMaxDatatypeTableSize());

        // Update the input options
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        var version = options.getVersion() == JellyConstants.PROTO_VERSION
            ? JellyConstants.PROTO_VERSION_1_0_X
            : JellyConstants.PROTO_VERSION;

        var newOptions = outputOptions.toBuilder().setVersion(version).build();
        rowBuffer.add(Rdf.RdfStreamRow.newBuilder().setOptions(newOptions).build());
    }
}
