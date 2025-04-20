package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import java.util.ArrayList;
import java.util.List;

public class ProtoTranscoderImpl implements ProtoTranscoder {

    private final RdfStreamOptions supportedInputOptions;
    private final RdfStreamOptions outputOptions;

    private final TranscoderLookup prefixLookup;
    private final TranscoderLookup nameLookup;
    private final TranscoderLookup datatypeLookup;

    private final List<RdfStreamRow> rowBuffer = new ArrayList<>();

    private RdfStreamOptions inputOptions = null;
    private boolean inputUsesPrefixes = false;
    private boolean hasChangedTerms = false;
    private boolean hasEmittedOptions = false;

    public ProtoTranscoderImpl(RdfStreamOptions supportedInputOptions, RdfStreamOptions outputOptions) {
        this.supportedInputOptions = supportedInputOptions;
        this.outputOptions = outputOptions;
        prefixLookup = new TranscoderLookup(false, outputOptions.getMaxPrefixTableSize());
        nameLookup = new TranscoderLookup(true, outputOptions.getMaxNameTableSize());
        datatypeLookup = new TranscoderLookup(false, outputOptions.getMaxDatatypeTableSize());
    }

    @Override
    public Iterable<RdfStreamRow> ingestRow(RdfStreamRow row) {
        rowBuffer.clear();
        processRow(row);
        return rowBuffer;
    }

    @Override
    public RdfStreamFrame ingestFrame(RdfStreamFrame frame) {
        rowBuffer.clear();
        for (final var row : frame.getRowsList()) {
            processRow(row);
        }

        return RdfStreamFrame.newBuilder().addAllRows(rowBuffer).putAllMetadata(frame.getMetadataMap()).build();
    }

    private void processRow(RdfStreamRow row) {
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
            case ROW_NOT_SET -> throw new RdfProtoTranscodingError("Row kind is not set");
        }
    }

    private void handleName(RdfStreamRow row) {
        final var name = row.getName();
        final var entry = nameLookup.addEntry(name.getId(), name.getValue());
        if (!entry.newEntry) {
            return;
        }

        if (entry.setId == name.getId()) {
            rowBuffer.add(row);
            return;
        }

        final var newName = RdfNameEntry.newBuilder().setId(entry.setId).setValue(name.getValue()).build();
        rowBuffer.add(RdfStreamRow.newBuilder().setName(newName).build());
    }

    private void handlePrefix(RdfStreamRow row) {
        final var prefix = row.getPrefix();
        final var entry = prefixLookup.addEntry(prefix.getId(), prefix.getValue());
        if (!entry.newEntry) {
            return;
        }

        if (entry.setId == prefix.getId()) {
            rowBuffer.add(row);
            return;
        }

        final var newPrefix = RdfPrefixEntry.newBuilder().setId(entry.setId).setValue(prefix.getValue()).build();
        rowBuffer.add(RdfStreamRow.newBuilder().setPrefix(newPrefix).build());
    }

    private void handleDatatype(RdfStreamRow row) {
        final var datatype = row.getDatatype();
        final var entry = datatypeLookup.addEntry(datatype.getId(), datatype.getValue());
        if (!entry.newEntry) {
            return;
        }

        if (entry.setId == datatype.getId()) {
            rowBuffer.add(row);
            return;
        }

        final var newDatatype = RdfDatatypeEntry.newBuilder().setId(entry.setId).setValue(datatype.getValue()).build();
        rowBuffer.add(RdfStreamRow.newBuilder().setDatatype(newDatatype).build());
    }

    private void handleIdentity(RdfStreamRow row) {
        // No changes needed, just add the row to the buffer
        rowBuffer.add(row);
    }

    private void handleTriple(RdfStreamRow row) {
        this.hasChangedTerms = false;
        final var triple = RdfTerm.from(row.getTriple());

        final var s1 = handleSpoTerm(triple.subject());
        final var p1 = handleSpoTerm(triple.predicate());
        final var o1 = handleSpoTerm(triple.object());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        final var newTriple = new RdfTerm.Triple(s1, p1, o1);
        rowBuffer.add(RdfStreamRow.newBuilder().setTriple(newTriple.toProto()).build());
    }

    private void handleQuad(RdfStreamRow row) {
        this.hasChangedTerms = false;
        final var quad = RdfTerm.from(row.getQuad());

        final var s1 = handleSpoTerm(quad.subject());
        final var p1 = handleSpoTerm(quad.predicate());
        final var o1 = handleSpoTerm(quad.object());
        final var g1 = handleGraphTerm(quad.graph());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        final var newQuad = new RdfTerm.Quad(s1, p1, o1, g1);
        rowBuffer.add(RdfStreamRow.newBuilder().setQuad(newQuad.toProto()).build());
    }

    private void handleGraphStart(RdfStreamRow row) {
        this.hasChangedTerms = false;
        final var graphStart = RdfTerm.from(row.getGraphStart());

        final var g1 = handleGraphTerm(graphStart.graph());
        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        final var newGraphStart = new RdfTerm.GraphStart(g1);
        rowBuffer.add(RdfStreamRow.newBuilder().setGraphStart(newGraphStart.toProto()).build());
    }

    private void handleNamespaceDeclaration(RdfStreamRow row) {
        this.hasChangedTerms = false;
        var nsRow = row.getNamespace();
        var iriValue = handleIri(RdfTerm.from(nsRow.getValue()));

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        var namespace = RdfNamespaceDeclaration.newBuilder()
            .setName(nsRow.getName())
            .setValue(iriValue.toProto())
            .build();

        rowBuffer.add(RdfStreamRow.newBuilder().setNamespace(namespace).build());
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

    private void handleOptions(RdfStreamOptions options) {
        if (supportedInputOptions != null) {
            if (outputOptions.getPhysicalType() != options.getPhysicalType()) {
                throw new RdfProtoTranscodingError(
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
            throw new RdfProtoTranscodingError("Output stream uses prefixes, but the input stream does not.");
        }

        nameLookup.newInputStream(options.getMaxNameTableSize());
        datatypeLookup.newInputStream(options.getMaxDatatypeTableSize());

        // Set the input options
        inputOptions = options;

        // Update the input options
        if (hasEmittedOptions) {
            return;
        }

        hasEmittedOptions = true;
        var version = inputOptions.getVersion() == JellyConstants.PROTO_VERSION_1_0_X
            ? JellyConstants.PROTO_VERSION_1_0_X
            : JellyConstants.PROTO_VERSION;

        var newOptions = outputOptions.toBuilder().setVersion(version).build();
        rowBuffer.add(RdfStreamRow.newBuilder().setOptions(newOptions).build());
    }
}
