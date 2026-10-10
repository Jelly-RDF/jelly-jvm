package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.internal.ColumnEncoder;
import eu.neverblink.jelly.core.internal.ColumnEncoder.Column;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import java.util.List;

/**
 * Implementation of SparqlEncoder.
 * <p>
 * Builds the columnar frames, one column per variable. The columns themselves are encoded by
 * the ColumnEncoder shared with Jelly-RDF.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@InternalApi
public final class SparqlEncoderImpl<TNode> extends SparqlEncoder<TNode> {

    // Frames are ended after this many rows, so that what we write stays readable
    // by a reader running with the default row limit.
    private static final int ROW_LIMIT_PER_FRAME = JellySparqlConstants.DEFAULT_MAX_ROWS_PER_FRAME;

    private static final String DEFAULT_GRAPH_ERROR = "The default graph is not a valid SPARQL result binding.";

    private final ColumnEncoder<TNode> columnEncoder;

    private String[] variableNames = null;
    private Column<TNode>[] columns = null;
    private int rowCount = 0;
    // Whether the next frame is the first frame of the stream, which contains the stream options
    private boolean firstFrame = true;
    // Whether the next frame is the first frame of a result set, which contains the header
    private boolean resultSetStart = true;

    // True while the buffers still hold the contents of the frame endFrame last returned.
    private boolean framePending = false;

    // Special flags to be used on the rowCount field.
    // They are only relevant where rowCount is not used – this way we don't need additional
    // fields in this class, which would take up another cache line.
    private static final int ROW_COUNT_ROW_FAILED = -1;
    private static final int ROW_COUNT_STREAM_ENDED = -2;

    /**
     * @param converter the converter to use
     * @param params parameters for the encoder
     */
    public SparqlEncoderImpl(ProtoEncoderConverter<TNode> converter, SparqlEncoder.Params params) {
        super(converter, params);
        if (options.getRdfVersion() == null) {
            throw new RdfProtoSerializationError("Unknown RDF version: %d".formatted(options.getRdfVersionValue()));
        }
        if (options.getStreamType() == null) {
            throw new RdfProtoSerializationError("Unknown stream type: %d".formatted(options.getStreamTypeValue()));
        }
        columnEncoder = new ColumnEncoder<>(
            converter,
            options.getMaxNameTableSize(),
            options.getMaxPrefixTableSize(),
            options.getMaxDatatypeTableSize(),
            options.getRdfVersion()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public void setVariables(List<String> variables) {
        if (rowCount < 0) {
            throw notUsable("setting the variables");
        }
        if (variableNames != null) {
            throw new RdfProtoSerializationError("Variables have already been set.");
        }
        final String[] names = variables.toArray(String[]::new);
        for (final String name : names) {
            if (name == null || name.isEmpty()) {
                throw new RdfProtoSerializationError("Variable names must not be empty.");
            }
        }
        variableNames = names;
        columns = new Column[variableNames.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = columnEncoder.newColumn(ColumnEncoder.TERMS_ALL, "binding", DEFAULT_GRAPH_ERROR);
        }
        // Only the budget – the lookup entries of a frame still pending are cleared once
        // this result set starts writing (see beginFrame)
        columnEncoder.resetUsedIds(columns, columns.length);
    }

    @Override
    public boolean appendRow(TNode[] row) {
        if (columns == null) {
            throw notUsable("appending rows");
        }
        if (row.length != columns.length) {
            throw new RdfProtoSerializationError(
                "Expected %d bindings in the row, got %d.".formatted(columns.length, row.length)
            );
        }
        beginFrame();
        // A frame that already holds rows is ended rather than overfilled. An empty frame takes
        // the row whatever it costs – ending it again would not make any more room.
        if (rowCount > 0 && !hasRoomForAnotherRow()) {
            return false;
        }
        try {
            for (int i = 0; i < row.length; i++) {
                columnEncoder.addCell(columns[i], row[i]);
            }
        } catch (Throwable e) {
            // The frame is now half-written, and cannot be completed
            columns = null;
            rowCount = ROW_COUNT_ROW_FAILED;
            throw e;
        }
        rowCount++;
        return true;
    }

    private RdfProtoSerializationError notUsable(String action) {
        if (variableNames == null) {
            return new RdfProtoSerializationError("Variables must be set before %s.".formatted(action));
        }
        if (rowCount == ROW_COUNT_ROW_FAILED) {
            return new RdfProtoSerializationError(
                "A previous row failed to encode, so the current frame cannot be completed. " +
                    "Use endStream(String) to end the stream with an error."
            );
        }
        return new RdfProtoSerializationError("The stream has already been ended.");
    }

    /**
     * Whether one more row can be encoded without overflowing the frame. Checked before the row
     * is touched, so that a full frame can be ended and the row retried – once the lookups have
     * been mutated, neither is possible any more.
     */
    private boolean hasRoomForAnotherRow() {
        // A table with its remaining-budget counter below zero has no room left
        return rowCount < ROW_LIMIT_PER_FRAME && columnEncoder.hasRoom();
    }

    /**
     * Discards the previous frame's state, if it is still around. Called before anything is written
     * into the buffers, so that the frame the caller got from endFrame stays readable for as long
     * as the contract promises.
     */
    private void beginFrame() {
        if (!framePending) {
            return;
        }
        framePending = false;
        columnEncoder.resetFrame(columns, columns.length);
        rowCount = 0;
    }

    @Override
    public SparqlResultsFrame endFrame() {
        if (columns == null) {
            throw notUsable("ending a frame");
        }
        return buildFrame(null);
    }

    @Override
    public SparqlResultsFrame endStream() {
        if (columns == null) {
            throw notUsable("ending the stream");
        }
        final SparqlResultsFrame frame = buildFrame(SparqlResultsTrailer.newInstance());
        endEncoder();
        return frame;
    }

    @Override
    public SparqlResultsFrame endStream(String error) {
        if (error == null || error.isEmpty()) {
            throw new RdfProtoSerializationError("The error message of an incomplete result set must not be empty.");
        }
        final SparqlResultsTrailer trailer = SparqlResultsTrailer.newInstance().setError(error);
        if (columns != null) {
            final SparqlResultsFrame frame = buildFrame(trailer);
            endEncoder();
            return frame;
        }
        if (variableNames == null || rowCount != ROW_COUNT_ROW_FAILED) {
            throw notUsable("ending the stream");
        }
        endEncoder();
        return failedRowFrame(trailer);
    }

    @Override
    public SparqlResultsFrame endResultSet() {
        requirePunctuated("endResultSet");
        if (columns == null) {
            throw notUsable("ending the result set");
        }
        final SparqlResultsFrame frame = buildFrame(SparqlResultsTrailer.newInstance());
        nextResultSet();
        return frame;
    }

    @Override
    public SparqlResultsFrame endResultSet(String error) {
        requirePunctuated("endResultSet");
        if (columns == null) {
            // Either a row failed to encode, which ends the encoder, or the call is invalid
            return endStream(error);
        }
        if (error == null || error.isEmpty()) {
            throw new RdfProtoSerializationError("The error message of an incomplete result set must not be empty.");
        }
        final SparqlResultsFrame frame = buildFrame(SparqlResultsTrailer.newInstance().setError(error));
        nextResultSet();
        return frame;
    }

    @Override
    public SparqlResultsFrame askResult(boolean value) {
        requirePunctuated("askResult");
        if (variableNames != null || rowCount < 0) {
            throw new RdfProtoSerializationError(
                "A boolean result can only be written at the start of the stream or after the previous result set was ended."
            );
        }
        // The frame does not use the encoder's buffers, so a pending frame stays readable
        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance()
            .setAskResult(SparqlAskResult.newInstance().setValue(value))
            .setTrailer(SparqlResultsTrailer.newInstance());
        if (firstFrame) {
            frame.setOptions(options);
            firstFrame = false;
        }
        frame.getSerializedSize();
        return frame;
    }

    private void requirePunctuated(String method) {
        if (options.getStreamType() != SparqlStreamType.PUNCTUATED) {
            throw new RdfProtoSerializationError(
                "%s can only be used in PUNCTUATED streams. Use endStream or askResultFrame instead.".formatted(method)
            );
        }
    }

    /**
     * Gets ready for the next result set of a PUNCTUATED stream. The lookups are kept, and the
     * frame just built stays readable: the next result set gets new column buffers, and the
     * shared lookup entry buffers are only cleared when it starts writing.
     */
    private void nextResultSet() {
        variableNames = null;
        columns = null;
        resultSetStart = true;
    }

    private void endEncoder() {
        // The returned frame still points at the buffers, which stay untouched from now on
        columns = null;
        rowCount = ROW_COUNT_STREAM_ENDED;
    }

    /**
     * The last frame of a stream whose last row failed to encode. The frame's content cannot be
     * encoded consistently anymore, but nothing follows the trailer, so it is fine to drop it,
     * along with the lookup entries it needed.
     */
    private SparqlResultsFrame failedRowFrame(SparqlResultsTrailer trailer) {
        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance();
        if (firstFrame) {
            frame.setOptions(options);
            firstFrame = false;
        }
        if (resultSetStart) {
            for (final String name : variableNames) {
                frame.addVariables(name);
            }
            resultSetStart = false;
        }
        frame.setTrailer(trailer);
        frame.getSerializedSize();
        return frame;
    }

    /**
     * Builds the frame from the buffers.
     *
     * @param trailer the trailer to attach, or null for none
     */
    private SparqlResultsFrame buildFrame(SparqlResultsTrailer trailer) {
        beginFrame();
        columnEncoder.endRuns(columns);

        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance();
        if (firstFrame) {
            frame.setOptions(options);
        }

        if (resultSetStart) {
            for (final String name : variableNames) {
                frame.addVariables(name);
            }
        }

        if (trailer != null) {
            frame.setTrailer(trailer);
        }
        frame.setRowCount(rowCount);
        frame.setNames(columnEncoder.nameEntries());
        frame.setPrefixes(columnEncoder.prefixEntries());
        frame.setDatatypes(columnEncoder.datatypeEntries());
        // A frame with no rows may leave out its columns altogether
        if (rowCount > 0) {
            addColumns(frame);
        }

        firstFrame = false;
        resultSetStart = false;
        // The frame points at the encoder's buffers, so they stay untouched until the next frame
        framePending = true;

        // Pre-calculate the serialized size, while all objects are likely still in cache.
        frame.getSerializedSize();
        return frame;
    }

    /** Emits one column per variable, in variable order. */
    private void addColumns(SparqlResultsFrame.Mutable frame) {
        for (final Column<TNode> col : columns) {
            frame.addColumns(columnEncoder.buildColumn(col));
        }
    }
}
