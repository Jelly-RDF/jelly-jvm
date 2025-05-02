package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Fast implementation of the ProtoTranscoder interface.
 * <p>
 * It does not in perfect compression (like you would get with full decoding and re-encoding), but it should be
 * good enough for the vast majority of cases.
 */
@InternalApi
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

    /**
     * Constructor for the ProtoTranscoderImpl class.
     *
     * @param supportedInputOptions maximum allowable options for the input streams (optional)
     * @param outputOptions options for the output stream. This MUST have the physical stream type set.
     */
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
        for (final var row : frame.getRows()) {
            processRow(row);
        }
        final var outFrame = RdfStreamFrame.newInstance();
        outFrame.getRows().addAll(rowBuffer);
        outFrame.getMetadata().addAll(frame.getMetadata());
        return outFrame;
    }

    private void processRow(RdfStreamRow row) {
        switch (row.getRowFieldNumber()) {
            case RdfStreamRow.OPTIONS -> handleOptions(row.getOptions());
            case RdfStreamRow.TRIPLE -> handleTriple(row);
            case RdfStreamRow.QUAD -> handleQuad(row);
            case RdfStreamRow.GRAPH_START -> handleGraphStart(row);
            case RdfStreamRow.GRAPH_END -> handleIdentity(row);
            case RdfStreamRow.NAMESPACE -> handleNamespaceDeclaration(row);
            case RdfStreamRow.NAME -> handleName(row);
            case RdfStreamRow.PREFIX -> handlePrefix(row);
            case RdfStreamRow.DATATYPE -> handleDatatype(row);
            default -> throw new RdfProtoTranscodingError("Row kind is not set");
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

        final var newName = RdfNameEntry.newInstance().setId(entry.setId).setValue(name.getValue());
        rowBuffer.add(RdfStreamRow.newInstance().setName(newName));
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

        final var newPrefix = RdfPrefixEntry.newInstance().setId(entry.setId).setValue(prefix.getValue());
        rowBuffer.add(RdfStreamRow.newInstance().setPrefix(newPrefix));
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

        final var newDatatype = RdfDatatypeEntry.newInstance().setId(entry.setId).setValue(datatype.getValue());
        rowBuffer.add(RdfStreamRow.newInstance().setDatatype(newDatatype));
    }

    private void handleIdentity(RdfStreamRow row) {
        // No changes needed, just add the row to the buffer
        rowBuffer.add(row);
    }

    private void handleTriple(RdfStreamRow row) {
        this.hasChangedTerms = false;
        final var triple = row.getTriple();

        final var s1 = handleSpoTerm(triple.getSubjectFieldNumber() - RdfTriple.S_IRI, triple.getSubject());
        final var p1 = handleSpoTerm(triple.getPredicateFieldNumber() - RdfTriple.P_IRI, triple.getPredicate());
        final var o1 = handleSpoTerm(triple.getObjectFieldNumber() - RdfTriple.O_IRI, triple.getObject());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        final var newTriple = RdfTriple.newInstance()
            .setSubject(s1, triple.getSubjectFieldNumber())
            .setPredicate(p1, triple.getPredicateFieldNumber())
            .setObject(o1, triple.getObjectFieldNumber());
        rowBuffer.add(RdfStreamRow.newInstance().setTriple(newTriple));
    }

    private void handleQuad(RdfStreamRow row) {
        this.hasChangedTerms = false;
        final var quad = row.getQuad();

        final var s1 = handleSpoTerm(quad.getSubjectFieldNumber() - RdfQuad.S_IRI, quad.getSubject());
        final var p1 = handleSpoTerm(quad.getPredicateFieldNumber() - RdfQuad.P_IRI, quad.getPredicate());
        final var o1 = handleSpoTerm(quad.getObjectFieldNumber() - RdfQuad.O_IRI, quad.getObject());
        final var g1 = handleGraphTerm(quad.getGraphFieldNumber() - RdfQuad.G_IRI, quad.getGraph());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        final var newQuad = RdfQuad.newInstance()
            .setSubject(s1, quad.getSubjectFieldNumber())
            .setPredicate(p1, quad.getPredicateFieldNumber())
            .setObject(o1, quad.getObjectFieldNumber())
            .setGraph(g1, quad.getGraphFieldNumber());
        rowBuffer.add(RdfStreamRow.newInstance().setQuad(newQuad));
    }

    private void handleGraphStart(RdfStreamRow row) {
        this.hasChangedTerms = false;
        final var graphStart = row.getGraphStart();

        final var g1 = handleGraphTerm(graphStart.getGraphFieldNumber() - RdfGraphStart.G_IRI, graphStart.getGraph());
        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        final var newGraphStart = RdfGraphStart.newInstance()
            .setGraph(g1, graphStart.getGraphFieldNumber());
        rowBuffer.add(RdfStreamRow.newInstance().setGraphStart(newGraphStart));
    }

    private void handleNamespaceDeclaration(RdfStreamRow row) {
        this.hasChangedTerms = false;
        var nsRow = row.getNamespace();
        var iriValue = handleIri(nsRow.getValue());

        if (!hasChangedTerms) {
            rowBuffer.add(row);
            return;
        }

        var namespace = RdfNamespaceDeclaration.newInstance().setName(nsRow.getName()).setValue(iriValue);

        rowBuffer.add(RdfStreamRow.newInstance().setNamespace(namespace));
    }

    private Object handleSpoTerm(int kind, Object term) {
        switch (kind) {
            case -1 -> {
                return null;
            }
            case 0 -> {
                return handleIri((RdfIri) term);
            }
            case 1 -> {
                return term; // blank node
            }
            case 2 -> {
                return handleLiteral((RdfLiteral) term);
            }
            case 3 -> {
                return handleTripleTerm((RdfTriple) term);
            }
            default -> {
                throw new RdfProtoTranscodingError("Unknown term type");
            }
        }
    }

    private Object handleGraphTerm(int kind, Object graph) {
        switch (kind) {
            case -1 -> {
                return null;
            }
            case 0 -> {
                return handleIri((RdfIri) graph);
            }
            case 1 -> {
                return graph; // blank node
            }
            case 2 -> {
                return handleLiteral((RdfLiteral) graph);
            }
            case 3 -> {
                return graph; // default graph
            }
            default -> {
                throw new RdfProtoTranscodingError("Unknown graph term type");
            }
        }
    }

    private RdfIri handleIri(RdfIri iri) {
        var prefix = iri.getPrefixId();
        var name = iri.getNameId();
        var prefix1 = inputUsesPrefixes ? prefixLookup.remap(prefix) : 0;
        var name1 = nameLookup.remap(name);
        if (prefix1 != prefix || name1 != name) {
            hasChangedTerms = true;
            return RdfIri.newInstance().setPrefixId(prefix1).setNameId(name1);
        }
        return iri;
    }

    private RdfLiteral handleLiteral(RdfLiteral literal) {
        if (!literal.hasDatatype()) {
            return literal;
        }

        var dt = literal.getDatatype();
        var dt1 = datatypeLookup.remap(dt);
        if (dt1 != dt) {
            hasChangedTerms = true;
            return RdfLiteral.newInstance().setLex(literal.getLex()).setDatatype(dt1);
        }

        return literal;
    }

    private RdfTriple handleTripleTerm(RdfTriple triple) {
        var s1 = handleSpoTerm(triple.getSubjectFieldNumber() - RdfTriple.S_IRI, triple.getSubject());
        var p1 = handleSpoTerm(triple.getPredicateFieldNumber() - RdfTriple.P_IRI, triple.getPredicate());
        var o1 = handleSpoTerm(triple.getObjectFieldNumber() - RdfTriple.O_IRI, triple.getObject());
        // Reference comparison is fine here, as the term objects should be reused directly if possible.
        if (s1 != triple.getSubject() || p1 != triple.getPredicate() || o1 != triple.getObject()) {
            hasChangedTerms = true;
            return RdfTriple.newInstance()
                .setSubject(s1, triple.getSubjectFieldNumber())
                .setPredicate(p1, triple.getPredicateFieldNumber())
                .setObject(o1, triple.getObjectFieldNumber());
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

        var newOptions = outputOptions.clone().setVersion(version);
        rowBuffer.add(RdfStreamRow.newInstance().setOptions(newOptions));
    }
}
