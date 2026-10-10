package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.ColumnDecoder;
import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfColumn;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of SparqlDecoder.
 *
 * @param <TNode> the type of RDF nodes in the library
 * @param <TDatatype> the type of RDF datatypes in the library
 */
@InternalApi
public final class SparqlDecoderImpl<TNode, TDatatype> extends DecoderBase<TNode, TDatatype> implements SparqlDecoder {

    private final SparqlResultsHandler<TNode> handler;
    // Whether the handler keeps the columns of a frame (see SparqlResultsHandler.keepsColumns)
    private final boolean freshColumns;
    private final SparqlResultsOptions supportedOptions;
    private final int maxRowsPerFrame;
    private final int maxValuesPerFrame;
    private final ColumnDecoder<TNode, TDatatype> columnDecoder = new ColumnDecoder<>(this);

    private SparqlResultsOptions currentOptions = null;
    // Variables of the result set, as declared by its first header. Kept across options resets, as
    // every header of a FLAT stream must declare the same ones.
    private String[] variableNames = null;
    // Whether a header is in effect: column i of a frame holds variable i
    private boolean headerInEffect = false;
    private TNode[] rowBuffer = null;
    // Per-variable column decode buffers, reused across frames unless the handler keeps them.
    // The inner arrays grow to the largest row count seen so far.
    private Object[][] decodedColumns = null;
    // Stream and result set flags, packed into one field. Only checked once per frame.
    private static final byte ASK_RESULT_RECEIVED = 1;
    // Set by a trailer, cleared by the next options message (FLAT) or result set (PUNCTUATED)
    private static final byte TRAILER_RECEIVED = 2;
    // Whether the next frame is the first frame of a result set: the first frame of the stream,
    // or in a PUNCTUATED stream, a frame that directly follows a trailer
    private static final byte RESULT_SET_START = 4;
    // Whether the stream is a sequence of result sets (PUNCTUATED), set by the first options
    private static final byte PUNCTUATED = 8;
    private byte flags = RESULT_SET_START;

    public SparqlDecoderImpl(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        SparqlResultsHandler<TNode> handler,
        SparqlResultsOptions supportedOptions,
        int maxRowsPerFrame,
        int maxValuesPerFrame
    ) {
        super(converter);
        this.handler = handler;
        this.freshColumns = handler.keepsColumns();
        this.supportedOptions =
            supportedOptions != null ? supportedOptions : JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS;
        this.maxRowsPerFrame = Math.min(maxRowsPerFrame, JellySparqlConstants.MAX_ROWS_PER_FRAME);
        this.maxValuesPerFrame = maxValuesPerFrame;
    }

    // The lookup tables are sized from the stream options, and the sizes are baked in when the
    // lookups are lazily created. ingestFrame rejects any frame content before the options are
    // received, so currentOptions is always set by the time these are called.

    @Override
    protected int getNameTableSize() {
        return currentOptions.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        return currentOptions.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        return currentOptions.getMaxDatatypeTableSize();
    }

    @Override
    public SparqlResultsOptions getSparqlOptions() {
        return currentOptions;
    }

    @Override
    public void ingestFrame(SparqlResultsFrame frame) {
        if (frame.getOptions() != null) {
            handleOptions(frame.getOptions());
        } else if (currentOptions == null) {
            throw new RdfProtoDeserializationError("Stream options were not received before the first frame content.");
        } else if ((flags & (PUNCTUATED | TRAILER_RECEIVED)) == TRAILER_RECEIVED) {
            throw new RdfProtoDeserializationError(
                "Received a frame after the stream trailer that does not contain stream options."
            );
        }
        if ((flags & (PUNCTUATED | RESULT_SET_START)) == (PUNCTUATED | RESULT_SET_START)) {
            startResultSet();
        }
        if ((flags & ASK_RESULT_RECEIVED) != 0) {
            handleFrameAfterAskResult(frame);
            return;
        }
        if (frame.getAskResult() != null) {
            handleAskResult(frame);
            // The next result set of a PUNCTUATED stream may use these
            applyLookupEntries(frame);
            endFrame(frame);
            return;
        }
        if (!frame.getVariables().isEmpty()) {
            if (frame.getOptions() == null && (flags & RESULT_SET_START) == 0) {
                throw new RdfProtoDeserializationError(
                    "The variables may only be set in the first frame of a result set, or with the stream options."
                );
            }
            handleHeader(frame.getVariables());
        } else if (!headerInEffect && (frame.getOptions() != null || (flags & RESULT_SET_START) != 0)) {
            if (variableNames != null && variableNames.length > 0) {
                throw new RdfProtoDeserializationError(
                    "A frame that repeats the stream options must repeat the result set header."
                );
            }
            // The first frame of a result set, or a frame containing options, with no header in
            // effect and no variables: a zero-variable result set.
            handleHeader(List.of());
        }
        if (!headerInEffect) {
            throw new RdfProtoDeserializationError("The result set header (variables) was not received.");
        }

        final int rows = frame.getRowCount();
        if (rows < 0 || rows > JellySparqlConstants.MAX_ROWS_PER_FRAME) {
            throw new RdfProtoDeserializationError(
                "Invalid row count %s: a frame may have at most %d rows.".formatted(
                    Integer.toUnsignedString(rows),
                    JellySparqlConstants.MAX_ROWS_PER_FRAME
                )
            );
        }
        if (rows > maxRowsPerFrame) {
            throw new RdfProtoDeserializationError(
                "The frame declares %d rows, more than the %d this reader accepts.".formatted(rows, maxRowsPerFrame)
            );
        }
        // Checked before anything is allocated: the decoder makes room for every value at once
        final long values = (long) rows * variableNames.length;
        if (values > maxValuesPerFrame) {
            throw new RdfProtoDeserializationError(
                "The frame declares %d rows of %d variables, %d values, more than the %d this reader accepts.".formatted(
                    rows,
                    variableNames.length,
                    values,
                    maxValuesPerFrame
                )
            );
        }

        applyLookupEntries(frame);

        final var columns = frame.getColumns();
        final int totalColumns = columns.size();
        // A frame with no rows may skip serializing columns
        final boolean noColumns = totalColumns == 0 && rows == 0;
        if (totalColumns != variableNames.length && !noColumns) {
            throw new RdfProtoDeserializationError(
                "The frame has %d columns, but the header declares %d variables.".formatted(
                    totalColumns,
                    variableNames.length
                )
            );
        }

        // Decode each variable's column into a row-indexed array
        if (freshColumns) {
            decodedColumns = new Object[variableNames.length][];
        }
        for (int v = 0; v < variableNames.length && !noColumns; v++) {
            final Object[] out = decodeBufferForVariable(v, rows);
            final RdfColumn column = get(columns, v);
            try {
                final ColumnDecoder<TNode, TDatatype>.Cursor cursor = columnDecoder.cursor(
                    column,
                    rows,
                    true,
                    "variable"
                );
                cursor.fill(out, 0, rows);
                cursor.finish();
            } catch (RdfProtoDeserializationError e) {
                throw e;
            } catch (Exception e) {
                throw new RdfProtoDeserializationError(
                    "Error while decoding the column for variable '%s': %s".formatted(variableNames[v], e),
                    e
                );
            }
        }

        handler.handleRows(decodedColumns, rows, rowBuffer);
        endFrame(frame);
    }

    /**
     * Applies all lookup entries of the frame. This happens before any column is decoded. In a
     * packed entry only the first value states its id, the rest are sequential.
     */
    private void applyLookupEntries(SparqlResultsFrame frame) {
        columnDecoder.applyLookupEntries(frame.getNames(), frame.getPrefixes(), frame.getDatatypes());
    }

    /**
     * Passes on the trailer of the frame, if any. In a PUNCTUATED stream, the trailer also ends
     * the result set, so the next frame starts a new one.
     */
    private void endFrame(SparqlResultsFrame frame) {
        final SparqlResultsTrailer trailer = frame.getTrailer();
        if (trailer != null) {
            flags |= TRAILER_RECEIVED;
            handler.handleTrailer(trailer.getError());
        }
        if (trailer != null && (flags & PUNCTUATED) != 0) {
            flags |= RESULT_SET_START;
        } else {
            flags &= ~RESULT_SET_START;
        }
    }

    /**
     * Forgets the previous result set of a PUNCTUATED stream. The lookups are kept.
     */
    private void startResultSet() {
        variableNames = null;
        headerInEffect = false;
        rowBuffer = null;
        decodedColumns = null;
        // The result set starts with the frame being ingested, so RESULT_SET_START stays set
        flags &= PUNCTUATED | RESULT_SET_START;
    }

    private Object[] decodeBufferForVariable(int variable, int rows) {
        Object[] buffer = decodedColumns[variable];
        if (buffer == null || buffer.length < rows) {
            buffer = new Object[rows];
            decodedColumns[variable] = buffer;
        }
        return buffer;
    }

    private static <T> T get(Iterable<T> collection, int index) {
        // The column collections are ArrayList-backed
        return ((List<T>) collection).get(index);
    }

    private void handleOptions(SparqlResultsOptions options) {
        JellySparqlOptions.checkCompatibility(options, supportedOptions);
        if (currentOptions != null) {
            if (options.getStreamTypeValue() != currentOptions.getStreamTypeValue()) {
                throw new RdfProtoDeserializationError(
                    "The stream type must be the same in all stream options of a stream."
                );
            }
            if ((flags & (PUNCTUATED | RESULT_SET_START)) == PUNCTUATED) {
                throw new RdfProtoDeserializationError(
                    "In a PUNCTUATED stream, the stream options may only be repeated in the first frame of a result set."
                );
            }
            // Repeated options (e.g., in concatenated streams) reset the stream state.
            resetLookups();
            headerInEffect = false;
        }
        currentOptions = options;
        columnDecoder.setRdfVersions(options.getRdfVersionValue(), supportedOptions.getRdfVersionValue());
        if (options.getStreamTypeValue() == SparqlStreamType.PUNCTUATED_VALUE) {
            flags |= PUNCTUATED;
        } else {
            flags &= ~(PUNCTUATED | TRAILER_RECEIVED);
        }
    }

    /**
     * A boolean result is a single frame: nothing may follow it in a FLAT stream, and in a
     * PUNCTUATED stream, it must have a trailer to end its result set.
     */
    private void handleFrameAfterAskResult(SparqlResultsFrame frame) {
        if (frame.getAskResult() != null) {
            throw new RdfProtoDeserializationError("Received more than one boolean (ASK) result.");
        }
        throw new RdfProtoDeserializationError(
            "No frame may follow the frame containing the boolean (ASK) result in the same result set."
        );
    }

    private void handleAskResult(SparqlResultsFrame frame) {
        if ((flags & RESULT_SET_START) == 0) {
            throw new RdfProtoDeserializationError(
                "A boolean (ASK) result may only be in the first frame of a result set."
            );
        }
        if (frame.getRowCount() != 0 || !frame.getVariables().isEmpty() || !frame.getColumns().isEmpty()) {
            throw new RdfProtoDeserializationError(
                "A frame with a boolean (ASK) result must not carry any bindings content."
            );
        }
        flags |= ASK_RESULT_RECEIVED;
        handler.handleAskResult(frame.getAskResult().getValue());
    }

    private void handleHeader(Iterable<String> variables) {
        final ArrayList<String> list = new ArrayList<>();
        variables.forEach(list::add);
        final String[] names = list.toArray(String[]::new);
        for (final String name : names) {
            if (name.isEmpty()) {
                throw new RdfProtoDeserializationError("Variable names must not be empty.");
            }
        }
        if (variableNames == null) {
            variableNames = names;
            rowBuffer = handler.createRowBuffer(names.length);
            if (rowBuffer == null || rowBuffer.length != names.length) {
                throw new RdfProtoDeserializationError("The handler's createRowBuffer returned an invalid buffer.");
            }
            decodedColumns = new Object[names.length][];
            handler.handleVariables(List.of(names));
        } else if (!Arrays.equals(variableNames, names)) {
            throw new RdfProtoDeserializationError(
                "A repeated header must declare the same variables in the same order as the original header."
            );
        }
        headerInEffect = true;
    }
}
