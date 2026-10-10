package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfEncoder;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.internal.ColumnEncoder.Column;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfColumnBatch;
import eu.neverblink.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.neverblink.jelly.core.proto.v1.RdfStreamFrame;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.jelly.core.proto.v1.RdfStreamType;

/**
 * Implementation of RdfEncoder: Jelly-RDF 1.2, column layout.
 * <p>
 * Each statement position (subject, predicate, object, graph) is one column, encoded by the
 * ColumnEncoder shared with Jelly-SPARQL.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@InternalApi
public final class RdfEncoderImpl<TNode> extends RdfEncoder<TNode> {

    // Only the object column can store literals
    private static final int DATATYPE_COLUMNS = 1;

    private ColumnEncoder<TNode> columnEncoder;
    private Column<TNode> subjects;
    private Column<TNode> predicates;
    private Column<TNode> objects;
    // null in TRIPLES streams
    private Column<TNode> graphs;
    private Column<TNode>[] columns;

    private final boolean quads;
    private final boolean messages;

    // The batch under construction. The frame wrapping it is only built when the frame ends.
    private RdfColumnBatch.Mutable batch;
    private int rowCount = 0;
    // Number of rows of the batch already assigned to a message
    private int rowsInMessages = 0;
    // Whether the next frame must state the options
    private boolean optionsPending = true;
    // Whether anything was written into a frame (including frames already passed to the sink)
    private boolean started = false;
    // Set when a statement fails to encode – the frame cannot be completed after that
    private boolean failed = false;

    /**
     * @param converter the converter to use
     * @param params parameters for the encoder
     */
    public RdfEncoderImpl(ProtoEncoderConverter<TNode> converter, RdfEncoder.Params params) {
        super(converter, params);
        checkOptions(options);
        this.quads = options.getPhysicalType() == PhysicalStreamType.QUADS;
        this.messages = options.getStreamType() == RdfStreamType.MESSAGES;
        newLookups();
        this.batch = RdfColumnBatch.newInstance();
    }

    private static void checkOptions(RdfStreamOptions options) {
        final PhysicalStreamType physicalType = options.getPhysicalType();
        if (physicalType != PhysicalStreamType.TRIPLES && physicalType != PhysicalStreamType.QUADS) {
            throw new RdfProtoSerializationError(
                (
                    "Jelly-RDF 1.2 streams must be of physical type TRIPLES or QUADS, got: %s. " +
                    "Use QUADS instead of GRAPHS, or write Jelly-RDF 1.1 (stream options version 2)."
                ).formatted(physicalType == null ? Integer.toString(options.getPhysicalTypeValue()) : physicalType)
            );
        }
        if (options.getStreamType() == null) {
            throw new RdfProtoSerializationError("Unknown stream type: %d".formatted(options.getStreamTypeValue()));
        }
        if (options.getRdfVersion() == null) {
            throw new RdfProtoSerializationError("Unknown RDF version: %d".formatted(options.getRdfVersionValue()));
        }
        if (options.getMaxNameTableSize() < BaseJellyOptions.MIN_COLUMN_NAME_TABLE_SIZE) {
            throw new RdfProtoSerializationError(
                "Requested name table size of %d is too small. The minimum is %d.".formatted(
                    options.getMaxNameTableSize(),
                    BaseJellyOptions.MIN_COLUMN_NAME_TABLE_SIZE
                )
            );
        }
    }

    /** Creates the lookups and the columns for the current options. */
    @SuppressWarnings("unchecked")
    private void newLookups() {
        columnEncoder = new ColumnEncoder<>(
            converter,
            options.getMaxNameTableSize(),
            options.getMaxPrefixTableSize(),
            options.getMaxDatatypeTableSize(),
            options.getRdfVersion()
        );
        subjects = columnEncoder.newColumn(
            ColumnEncoder.TERMS_RESOURCE,
            "subject",
            "The default graph cannot be the subject of a statement."
        );
        predicates = columnEncoder.newColumn(
            ColumnEncoder.TERMS_IRI,
            "predicate",
            "The default graph cannot be the predicate of a statement."
        );
        objects = columnEncoder.newColumn(
            ColumnEncoder.TERMS_ALL,
            "object",
            "The default graph cannot be the object of a statement."
        );
        if (quads) {
            graphs = columnEncoder.newColumn(ColumnEncoder.TERMS_RESOURCE, "graph", null);
            columns = new Column[] { subjects, predicates, objects, graphs };
        } else {
            graphs = null;
            columns = new Column[] { subjects, predicates, objects };
        }
        columnEncoder.resetFrame(columns, DATATYPE_COLUMNS);
    }

    @Override
    public void handleTriple(TNode subject, TNode predicate, TNode object) {
        if (quads) {
            // A triple in a QUADS stream is in the default graph
            handleQuad(subject, predicate, object, null);
            return;
        }
        beforeStatement();
        try {
            columnEncoder.addSubjectCell(subjects, subject);
            columnEncoder.addPredicateCell(predicates, predicate);
            columnEncoder.addObjectCell(objects, object);
        } catch (Throwable e) {
            failed = true;
            throw e;
        }
        rowCount++;
    }

    @Override
    public void handleQuad(TNode subject, TNode predicate, TNode object, TNode graph) {
        if (!quads) {
            throw new RdfProtoSerializationError("Cannot write quads to a TRIPLES stream.");
        }
        beforeStatement();
        try {
            columnEncoder.addSubjectCell(subjects, subject);
            columnEncoder.addPredicateCell(predicates, predicate);
            columnEncoder.addObjectCell(objects, object);
            columnEncoder.addGraphCell(graphs, graph);
        } catch (Throwable e) {
            failed = true;
            throw e;
        }
        rowCount++;
    }

    /**
     * Ends the frame first if it is full. A frame that is still empty takes the statement whatever
     * it costs – ending it would not make any more room.
     */
    private void beforeStatement() {
        checkUsable();
        if (rowCount >= frameSize || (hasContent() && !columnEncoder.hasRoom())) {
            endFrame();
        }
        started = true;
    }

    @Override
    public void handleNamespace(String prefix, TNode namespace) {
        checkUsable();
        // The namespace declarations of a batch come before its statements, so a declaration
        // that follows a statement goes into the next frame.
        if (rowCount > 0 || (hasContent() && !columnEncoder.hasRoom())) {
            endFrame();
        }
        started = true;
        try {
            batch.addNamespaces(
                RdfNamespaceDeclaration.newInstance()
                    .setName(prefix)
                    .setValue(columnEncoder.encodeNamespaceIri(namespace))
            );
        } catch (Throwable e) {
            failed = true;
            throw e;
        }
    }

    @Override
    public void handleMessageEnd() {
        checkUsable();
        if (!messages) {
            throw new RdfProtoSerializationError("Messages can only be ended in streams of type MESSAGES.");
        }
        started = true;
        batch.addMessageLengths(rowCount - rowsInMessages);
        rowsInMessages = rowCount;
    }

    @Override
    public void flush() {
        checkUsable();
        if (hasContent()) {
            endFrame();
        }
    }

    @Override
    public void restateOptions(RdfStreamOptions newOptions) {
        checkUsable();
        final RdfStreamOptions normalized = normalizeOptions(newOptions);
        checkOptions(normalized);
        if (
            normalized.getPhysicalTypeValue() != options.getPhysicalTypeValue() ||
            normalized.getStreamTypeValue() != options.getStreamTypeValue()
        ) {
            throw new RdfProtoSerializationError(
                "The physical stream type and the stream type must stay the same when the options are restated."
            );
        }
        if (started) {
            if (hasContent()) {
                endFrame();
            }
            options = normalized;
            newLookups();
            optionsPending = true;
        } else {
            // Nothing was written yet: the stream simply starts with the new options
            options = normalized;
            newLookups();
        }
    }

    private void checkUsable() {
        if (failed) {
            throw new RdfProtoSerializationError(
                "A previous statement failed to encode, so the current frame cannot be completed."
            );
        }
    }

    private boolean hasContent() {
        return rowCount > 0 || !batch.getNamespaces().isEmpty() || !batch.getMessageLengths().isEmpty();
    }

    /** Builds the frame, passes it to the sink, and starts the next one. */
    private void endFrame() {
        final RdfColumnBatch.Mutable batch = this.batch;
        columnEncoder.endRuns(columns);
        batch.setRowCount(rowCount);
        batch.setNames(columnEncoder.nameEntries());
        batch.setPrefixes(columnEncoder.prefixEntries());
        batch.setDatatypes(columnEncoder.datatypeEntries());
        // A batch with no rows may leave out its columns altogether
        if (rowCount > 0) {
            batch.setSubjects(columnEncoder.buildColumn(subjects));
            batch.setPredicates(columnEncoder.buildColumn(predicates));
            batch.setObjects(columnEncoder.buildColumn(objects));
            // If every quad is in the default graph, the graph column is left out
            if (graphs != null && !graphs.isAllUnbound()) {
                batch.setGraphs(columnEncoder.buildColumn(graphs));
            }
        }
        final RdfStreamFrame.Mutable frame = RdfStreamFrame.newInstance().setColumns(batch);
        if (optionsPending) {
            frame.addRows(RdfStreamRow.newInstance().setOptions(options));
        }
        try {
            // Pre-calculate the serialized size, while all objects are likely still in cache.
            frame.getSerializedSize();
            frameSink.accept(frame);
        } finally {
            optionsPending = false;
            rowCount = 0;
            rowsInMessages = 0;
            this.batch = RdfColumnBatch.newInstance();
            columnEncoder.resetFrame(columns, DATATYPE_COLUMNS);
        }
    }
}
