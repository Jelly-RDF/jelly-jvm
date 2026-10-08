package eu.neverblink.jelly.core.sparql.internal;

import com.google.protobuf.ByteString;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NameDecoder;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral2;
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of SparqlDecoder.
 *
 * @param <TNode> the type of RDF nodes in the library
 * @param <TDatatype> the type of RDF datatypes in the library
 */
@InternalApi
public final class SparqlDecoderImpl<TNode, TDatatype> extends DecoderBase<TNode, TDatatype> implements SparqlDecoder {

    // Run lengths of 0–14 are inlined in the layout token. 15 needs an extension varint.
    private static final int MAX_INLINE_LEN = 15;

    private static final String RDF_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";
    private static final String RDF_DIR_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#dirLangString";

    private final SparqlResultsHandler<TNode> handler;
    // Whether the handler keeps the columns of a frame (see SparqlResultsHandler.keepsColumns)
    private final boolean freshColumns;
    private final SparqlResultsOptions supportedOptions;
    private final int maxRowsPerFrame;

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
        int maxRowsPerFrame
    ) {
        super(converter);
        this.handler = handler;
        this.freshColumns = handler.keepsColumns();
        this.supportedOptions =
            supportedOptions != null ? supportedOptions : JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS;
        this.maxRowsPerFrame = Math.min(maxRowsPerFrame, JellySparqlConstants.MAX_ROWS_PER_FRAME);
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
                    "A frame that repeats the stream options must restate the result set header."
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
            final SparqlColumn column = get(columns, v);
            try {
                decodeColumn(columnReader(column), column.getLayouts(), rows, out);
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
        for (final RdfLookupEntryPacked entry : frame.getNames()) {
            int id = entry.getId();
            for (final String value : entry.getValues()) {
                getNameDecoder().updateNames(id, value);
                id = 0;
            }
        }
        for (final RdfLookupEntryPacked entry : frame.getPrefixes()) {
            int id = entry.getId();
            for (final String value : entry.getValues()) {
                getNameDecoder().updatePrefixes(id, value);
                id = 0;
            }
        }
        for (final RdfLookupEntryPacked entry : frame.getDatatypes()) {
            int id = entry.getId();
            for (final String value : entry.getValues()) {
                if (RDF_LANG_STRING.equals(value) || RDF_DIR_LANG_STRING.equals(value)) {
                    // A literal with this datatype must have a language tag, which the datatype
                    // form cannot carry
                    throw new RdfProtoDeserializationError("The datatype lookup must not contain %s.".formatted(value));
                }
                final TDatatype datatype;
                try {
                    datatype = converter.makeDatatype(value);
                } catch (RdfProtoDeserializationError e) {
                    throw e;
                } catch (Exception e) {
                    // Most likely the RDF library rejected the IRI
                    throw new RdfProtoDeserializationError(
                        "Error while decoding datatype '%s': %s".formatted(value, e),
                        e
                    );
                }
                getDatatypeLookup().update(id, datatype);
                id = 0;
            }
        }
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
                "A restated header must declare the same variables in the same order as the original header."
            );
        }
        headerInEffect = true;
    }

    /**
     * Picks the reader for a column: one of the fast readers if every value is of one type
     * (no kinds), or the polymorphic reader.
     */
    private ValueReader<TNode> columnReader(SparqlColumn column) {
        if (!column.getKinds().isEmpty()) {
            return new PolyReader(column);
        }
        final int iriCount = column.getNameIds().size();
        final int literalCount = column.getLexValues().size();
        final int bnodeCount = column.getBnodes().size();
        final int tripleCount = column.getTripleTerms().size();
        final int nonEmpty =
            (iriCount > 0 ? 1 : 0) + (literalCount > 0 ? 1 : 0) + (bnodeCount > 0 ? 1 : 0) + (tripleCount > 0 ? 1 : 0);
        if (nonEmpty > 1) {
            throw new RdfProtoDeserializationError(
                "Corrupt column: the values are of more than one type, but the column has no kinds."
            );
        }
        if (literalCount > 0) {
            return literalReader(column);
        } else if (bnodeCount > 0) {
            return new BnodeReader(column.getBnodes());
        } else if (tripleCount > 0) {
            return new PolyReader(column);
        }
        // IRIs, or no values at all
        return new IriReader(column);
    }

    /**
     * Per-column state for resolving the prefix_id / name_id inference of RdfIri values,
     * in column order (see the sparql.proto comments).
     */
    private final class IriState {

        // The lookup tables exist by the time a column is decoded
        private final NameDecoder<TNode> names = getNameDecoder();
        private int lastPrefixId = 0;
        private int lastNameId = 0;

        TNode decode(int prefixId, int nameId) {
            if (prefixId == 0) {
                prefixId = lastPrefixId;
            } else {
                lastPrefixId = prefixId;
            }
            if (nameId == 0) {
                nameId = lastNameId + 1;
            }
            lastNameId = nameId;
            return names.decodeRaw(prefixId, nameId);
        }
    }

    /**
     * Decodes the run values of one column, in order.
     */
    private abstract static class ValueReader<TNode> {

        /** The number of values not decoded yet. */
        abstract int remaining();

        /** Decodes the next value. The caller checks that there is one. */
        abstract TNode decodeNext();

        /**
         * Decodes the next {@code count} values into {@code out}, from {@code from} on. The caller
         * checks that there are that many.
         * <p>
         * Every reader implements this with the same loop over its own decodeNext(), so that the
         * call in the loop has one known target and is inlined. A single loop here would call
         * decodeNext() of whichever reader it is given.
         */
        abstract void decodeInto(Object[] out, int from, int count);
    }

    private final class IriReader extends ValueReader<TNode> {

        private final RepeatedInt nameIds;
        // Empty (every value has prefix id 0), one prefix for the whole column, or one entry
        // per value – in which case the RdfIri prefix inference applies along the list
        private final RepeatedInt prefixIds;
        private final IriState iriState = new IriState();
        private int index = 0;

        IriReader(SparqlColumn column) {
            this.nameIds = column.getNameIds();
            this.prefixIds = column.getPrefixIds();
            final int prefixCount = prefixIds.size();
            if (prefixCount > 1 && prefixCount != nameIds.size()) {
                throw new RdfProtoDeserializationError(
                    "Corrupt IRI column: %d prefix ids for %d name ids, expected 0, 1 or %d.".formatted(
                        prefixCount,
                        nameIds.size(),
                        nameIds.size()
                    )
                );
            }
        }

        @Override
        int remaining() {
            return nameIds.size() - index;
        }

        @Override
        void decodeInto(Object[] out, int from, int count) {
            for (int i = 0; i < count; i++) {
                out[from + i] = decodeNext();
            }
        }

        @Override
        TNode decodeNext() {
            final int i = index++;
            final int prefixCount = prefixIds.size();
            final int prefixId;
            if (prefixCount == 0) {
                prefixId = 0;
            } else if (prefixCount == 1) {
                prefixId = prefixIds.get(0);
            } else {
                prefixId = prefixIds.get(i);
            }
            return iriState.decode(prefixId, nameIds.get(i));
        }
    }

    private final class BnodeReader extends ValueReader<TNode> {

        private final RepeatedString values;
        private int index = 0;

        BnodeReader(RepeatedString values) {
            this.values = values;
        }

        @Override
        int remaining() {
            return values.size() - index;
        }

        @Override
        void decodeInto(Object[] out, int from, int count) {
            for (int i = 0; i < count; i++) {
                out[from + i] = decodeNext();
            }
        }

        @Override
        TNode decodeNext() {
            return converter.makeBlankNode(values.get(index++));
        }
    }

    /**
     * Picks the reader for a literal column: one kind for the whole column, or one per value
     * (see the sparql.proto comments).
     */
    private ValueReader<TNode> literalReader(SparqlColumn column) {
        final RepeatedString lexValues = column.getLexValues();
        final RepeatedInt kinds = column.getLiteralKinds();
        final int kindCount = kinds.size();
        if (kindCount > 1 && kindCount != lexValues.size()) {
            throw new RdfProtoDeserializationError(
                "Corrupt literal column: %d literal kinds for %d lexical forms, expected 0, 1 or %d.".formatted(
                    kindCount,
                    lexValues.size(),
                    lexValues.size()
                )
            );
        }
        final RepeatedString langtags = column.getLangtags();
        final RepeatedInt directions = column.getLangtagDirections();
        if (!directions.isEmpty() && directions.size() != langtags.size()) {
            throw new RdfProtoDeserializationError(
                "Corrupt literal column: %d base directions for %d language tags.".formatted(
                    directions.size(),
                    langtags.size()
                )
            );
        }
        if (lexValues.isEmpty()) {
            // Nothing to resolve: kinds and language tags only matter for the values that use them
            return new UniformLiteralReader(lexValues, null, 0);
        }
        final LiteralKinds resolved = new LiteralKinds(langtags, directions);
        if (kindCount == 0) {
            return new UniformLiteralReader(lexValues, resolved, 0);
        } else if (kindCount == 1) {
            return new UniformLiteralReader(lexValues, resolved, kinds.get(0));
        }
        return new MixedLiteralReader(lexValues, kinds, resolved);
    }

    /**
     * Resolves the literal kinds of one literal column: the datatypes from the lookup, and the
     * column's language tags with their base directions. A base direction is only checked once a
     * value uses its tag.
     */
    private final class LiteralKinds {

        private final RepeatedString langtags;
        // As read from the stream, parallel to langtags, or empty for no directions at all
        private final RepeatedInt directionValues;
        // Resolved on first use, null where the tag has no base direction
        private final RdfBaseDirection[] directions;
        private final boolean[] resolved;

        LiteralKinds(RepeatedString langtags, RepeatedInt directionValues) {
            this.langtags = langtags;
            this.directionValues = directionValues;
            this.directions = new RdfBaseDirection[langtags.size()];
            this.resolved = new boolean[langtags.size()];
        }

        /** The base direction of a language tag, or null for none. */
        RdfBaseDirection direction(int index) {
            if (!resolved[index]) {
                final int value = directionValues.isEmpty() ? 0 : directionValues.get(index);
                directions[index] = value == 0 ? null : baseDirection(value);
                resolved[index] = true;
            }
            return directions[index];
        }

        /** The datatype of a datatype kind (an odd kind). */
        TDatatype datatype(int kind) {
            return getDatatypeLookup().get((kind >>> 1) + 1);
        }

        /** The index in langtags of a language kind (an even kind above 0). */
        int langtagIndex(int kind) {
            final int index = (kind >>> 1) - 1;
            if (index >= langtags.size()) {
                throw new RdfProtoDeserializationError(
                    "Corrupt literal column: language tag %d referenced, but the column has %d.".formatted(
                        index,
                        langtags.size()
                    )
                );
            }
            return index;
        }

        TNode make(String lex, int kind) {
            if (kind == 0) {
                return converter.makeSimpleLiteral(lex);
            }
            if ((kind & 1) != 0) {
                return converter.makeDtLiteral(lex, datatype(kind));
            }
            final int index = langtagIndex(kind);
            final RdfBaseDirection direction = direction(index);
            return direction == null
                ? converter.makeLangLiteral(lex, langtags.get(index))
                : converter.makeDirLangLiteral(lex, langtags.get(index), direction);
        }
    }

    /**
     * Reader for a literal column in which every value has the same kind: the datatype or the
     * language tag is resolved once for the whole column.
     */
    private final class UniformLiteralReader extends ValueReader<TNode> {

        private final RepeatedString values;
        private final int kind;
        // Resolved once: the datatype of a datatype kind, or null
        private final TDatatype datatype;
        // Resolved once: the language tag and base direction of a language kind, or null
        private final String langtag;
        private final RdfBaseDirection direction;
        private int index = 0;

        UniformLiteralReader(RepeatedString values, LiteralKinds kinds, int kind) {
            this.values = values;
            this.kind = kind;
            if ((kind & 1) != 0) {
                this.datatype = kinds.datatype(kind);
                this.langtag = null;
                this.direction = null;
            } else if (kind != 0) {
                final int tag = kinds.langtagIndex(kind);
                this.datatype = null;
                this.langtag = kinds.langtags.get(tag);
                this.direction = kinds.direction(tag);
            } else {
                this.datatype = null;
                this.langtag = null;
                this.direction = null;
            }
        }

        @Override
        int remaining() {
            return values.size() - index;
        }

        @Override
        void decodeInto(Object[] out, int from, int count) {
            for (int i = 0; i < count; i++) {
                out[from + i] = decodeNext();
            }
        }

        @Override
        TNode decodeNext() {
            final String lex = values.get(index++);
            if (kind == 0) {
                return converter.makeSimpleLiteral(lex);
            }
            if (datatype != null) {
                return converter.makeDtLiteral(lex, datatype);
            }
            return direction == null
                ? converter.makeLangLiteral(lex, langtag)
                : converter.makeDirLangLiteral(lex, langtag, direction);
        }
    }

    /** Reader for a literal column with one literal kind per value. */
    private final class MixedLiteralReader extends ValueReader<TNode> {

        private final RepeatedString values;
        private final RepeatedInt kinds;
        private final LiteralKinds resolved;
        private int index = 0;

        MixedLiteralReader(RepeatedString values, RepeatedInt kinds, LiteralKinds resolved) {
            this.values = values;
            this.kinds = kinds;
            this.resolved = resolved;
        }

        @Override
        int remaining() {
            return values.size() - index;
        }

        @Override
        void decodeInto(Object[] out, int from, int count) {
            for (int i = 0; i < count; i++) {
                out[from + i] = decodeNext();
            }
        }

        @Override
        TNode decodeNext() {
            final int i = index++;
            return resolved.make(values.get(i), kinds.get(i));
        }
    }

    /**
     * Converts a literal in the full form. Every literal of the stream that is not in the lexical
     * form of a literal column goes through here.
     */
    private TNode convertLiteral(RdfLiteral2 literal) {
        final int direction = literal.getDirectionValue();
        switch (literal.getLiteralKindFieldNumber()) {
            case RdfLiteral2.LANGTAG -> {
                if (direction == 0) {
                    return converter.makeLangLiteral(literal.getLex(), literal.getLangtag());
                }
                return converter.makeDirLangLiteral(literal.getLex(), literal.getLangtag(), baseDirection(direction));
            }
            case RdfLiteral2.DATATYPE -> {
                if (direction != 0) {
                    throw directionWithoutLangtag();
                }
                return converter.makeDtLiteral(literal.getLex(), getDatatypeLookup().get(literal.getDatatype()));
            }
            default -> {
                if (direction != 0) {
                    throw directionWithoutLangtag();
                }
                return converter.makeSimpleLiteral(literal.getLex());
            }
        }
    }

    private static RdfProtoDeserializationError directionWithoutLangtag() {
        return new RdfProtoDeserializationError("A literal has a base direction, but no language tag.");
    }

    /** Checks a base direction read from the stream (LTR or RTL, never UNSPECIFIED). */
    private RdfBaseDirection baseDirection(int value) {
        final RdfBaseDirection direction = RdfBaseDirection.forNumber(value);
        if (direction == null || direction == RdfBaseDirection.UNSPECIFIED) {
            throw new RdfProtoDeserializationError("Unknown base direction: %d".formatted(value));
        }
        if (allowedRdfVersion() == RdfVersion.RDF_VERSION_1_1_VALUE) {
            throw notAllowed("literals with a base direction");
        }
        return direction;
    }

    /**
     * The RDF version the terms of the stream must conform to: the one the stream declares, or,
     * if it declares none, the one this reader supports. 0 (unspecified) allows all terms.
     */
    private int allowedRdfVersion() {
        final int declared = currentOptions.getRdfVersionValue();
        return declared != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE ? declared : supportedOptions.getRdfVersionValue();
    }

    private RdfProtoDeserializationError notAllowed(String what) {
        final int declared = currentOptions.getRdfVersionValue();
        final String version = RdfVersionUtils.rdfVersionName(allowedRdfVersion());
        return new RdfProtoDeserializationError(
            declared != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE
                ? "The stream declares %s, but contains %s.".formatted(version, what)
                : "The stream contains %s, but this reader only supports %s.".formatted(what, version)
        );
    }

    /**
     * Reader for a polymorphic column: the kinds field says which typed sub-column holds the next
     * value (see the sparql.proto comments).
     */
    private final class PolyReader extends ValueReader<TNode> {

        private static final int IRI = 0;
        private static final int LITERAL = 1;
        private static final int BNODE = 2;
        private static final int TRIPLE = 3;

        // For each byte of the kinds field, how many of its 4 values are of each kind, in 16-bit
        // lanes: IRIs in the lowest, then literals, blank nodes and triple terms
        private static final long[] KIND_COUNTS = new long[256];

        static {
            for (int b = 0; b < 256; b++) {
                long counts = 0;
                for (int i = 0; i < 4; i++) {
                    counts += 1L << (((b >>> (i << 1)) & 3) << 4);
                }
                KIND_COUNTS[b] = counts;
            }
        }

        private final byte[] kinds;
        private final int valueCount;
        private final ValueReader<TNode> iris;
        private final ValueReader<TNode> literals;
        private final ValueReader<TNode> bnodes;
        private final Iterator<RdfTripleTerm> tripleTerms;
        // The IRIs of the triple terms have an inference state of their own
        private final IriState tripleIriState = new IriState();
        private int index = 0;

        PolyReader(SparqlColumn column) {
            final int iriCount = column.getNameIds().size();
            final int literalCount = column.getLexValues().size();
            final int bnodeCount = column.getBnodes().size();
            final int tripleCount = column.getTripleTerms().size();
            this.valueCount = iriCount + literalCount + bnodeCount + tripleCount;
            ByteString kindsField = column.getKinds();
            if (kindsField.isEmpty()) {
                // Only triple terms (see columnReader)
                final byte[] filled = new byte[(valueCount + 3) >>> 2];
                Arrays.fill(filled, (byte) 0xff);
                if ((valueCount & 3) != 0) {
                    filled[filled.length - 1] = (byte) ((1 << ((valueCount & 3) << 1)) - 1);
                }
                kindsField = ByteString.copyFrom(filled);
            }
            if (kindsField.size() != (valueCount + 3) >>> 2) {
                throw new RdfProtoDeserializationError(
                    "Corrupt polymorphic column: %d bytes of kinds for %d values.".formatted(
                        kindsField.size(),
                        valueCount
                    )
                );
            }
            // A copy, which is read faster than the ByteString, and a quarter of the value count long
            this.kinds = kindsField.toByteArray();
            // Check that the kinds agree with the sub-columns, so that decoding cannot run out of
            // values in one of them. The bytes that hold 4 values are counted by the table, 16383
            // at most at a time, so that no 16-bit lane overflows.
            final int[] counts = new int[4];
            final int fullBytes = valueCount >>> 2;
            for (int start = 0; start < fullBytes; start += 16383) {
                final int end = Math.min(fullBytes, start + 16383);
                long lanes = 0;
                for (int b = start; b < end; b++) {
                    lanes += KIND_COUNTS[kinds[b] & 0xff];
                }
                for (int k = 0; k < 4; k++) {
                    counts[k] += (int) (lanes >>> (k << 4)) & 0xffff;
                }
            }
            for (int i = fullBytes << 2; i < valueCount; i++) {
                counts[kindAt(i)]++;
            }
            if (
                counts[IRI] != iriCount ||
                counts[LITERAL] != literalCount ||
                counts[BNODE] != bnodeCount ||
                counts[TRIPLE] != tripleCount
            ) {
                throw new RdfProtoDeserializationError(
                    "Corrupt polymorphic column: the kinds do not match the number of values in the sub-columns."
                );
            }
            final int unusedBits = (valueCount & 3) == 0 ? 0 : 8 - ((valueCount & 3) << 1);
            if (unusedBits != 0 && (kinds[kinds.length - 1] & 0xff) >>> (8 - unusedBits) != 0) {
                throw new RdfProtoDeserializationError(
                    "Corrupt polymorphic column: unused bits of the kinds are not 0."
                );
            }
            this.iris = iriCount == 0 ? null : new IriReader(column);
            this.literals = literalCount == 0 ? null : literalReader(column);
            this.bnodes = bnodeCount == 0 ? null : new BnodeReader(column.getBnodes());
            this.tripleTerms = column.getTripleTerms().iterator();
        }

        private int kindAt(int i) {
            return (kinds[i >>> 2] >>> ((i & 3) << 1)) & 3;
        }

        @Override
        int remaining() {
            return valueCount - index;
        }

        @Override
        void decodeInto(Object[] out, int from, int count) {
            for (int i = 0; i < count; i++) {
                out[from + i] = decodeNext();
            }
        }

        @Override
        TNode decodeNext() {
            // The counts were checked up front, so the sub-column always has the value
            return switch (kindAt(index++)) {
                case IRI -> iris.decodeNext();
                case LITERAL -> literals.decodeNext();
                case BNODE -> bnodes.decodeNext();
                default -> {
                    if (allowedRdfVersion() < RdfVersion.RDF_VERSION_1_2_VALUE) {
                        throw notAllowed("triple terms");
                    }
                    yield decodeTripleTerm(tripleTerms.next());
                }
            };
        }

        /**
         * Decodes a triple term. Its IRIs take part in the IRI inference of the column's triple
         * terms, in the order subject, predicate, object. The nesting depth is already limited by
         * the parser.
         */
        private TNode decodeTripleTerm(RdfTripleTerm triple) {
            final TNode s = switch (triple.getSubjectFieldNumber()) {
                case RdfTripleTerm.S_IRI -> decodeIri(triple.getSIri());
                case RdfTripleTerm.S_BNODE -> converter.makeBlankNode(triple.getSBnode());
                default -> throw new RdfProtoDeserializationError("A triple term has no subject.");
            };
            if (triple.getPIri() == null) {
                throw new RdfProtoDeserializationError("A triple term has no predicate.");
            }
            final TNode p = decodeIri(triple.getPIri());
            final TNode o = switch (triple.getObjectFieldNumber()) {
                case RdfTripleTerm.O_IRI -> decodeIri(triple.getOIri());
                case RdfTripleTerm.O_BNODE -> converter.makeBlankNode(triple.getOBnode());
                case RdfTripleTerm.O_LITERAL -> convertLiteral(triple.getOLiteral());
                case RdfTripleTerm.O_TRIPLE_TERM -> decodeTripleTerm(triple.getOTripleTerm());
                default -> throw new RdfProtoDeserializationError("A triple term has no object.");
            };
            return converter.makeTripleNode(s, p, o);
        }

        private TNode decodeIri(RdfIri iri) {
            return tripleIriState.decode(iri.getPrefixId(), iri.getNameId());
        }
    }

    /**
     * Decodes one column: walks the sequence layout, materializing the cells of the column into
     * {@code out}. Cells past the encoded sequence, up to the frame row count, are unbound
     * (nulls). The buffer may be longer than {@code rows}. Cells past it are left untouched.
     */
    private void decodeColumn(ValueReader<TNode> reader, RepeatedInt layout, int rows, Object[] out) {
        int pos = 0;
        final int layoutSize = layout.size();
        for (int k = 0; k < layoutSize; k++) {
            final int token = layout.get(k);
            final int skip = token >>> 5;
            final int kind = token & 0b10000;
            long len = token & MAX_INLINE_LEN;
            if (len == MAX_INLINE_LEN) {
                k++;
                if (k >= layoutSize) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: an escaped length token is not followed by an extension."
                    );
                }
                len = MAX_INLINE_LEN + Integer.toUnsignedLong(layout.get(k));
            }
            if (skip > rows - pos) {
                throw new RdfProtoDeserializationError("Corrupt column layout: more cells than the frame row count.");
            }
            if (skip > reader.remaining()) {
                throw new RdfProtoDeserializationError("Corrupt column layout: not enough values in the column.");
            }
            reader.decodeInto(out, pos, skip);
            pos += skip;
            if (kind == 0) {
                // Repeat run
                final long count = len + 2;
                if (count > rows - pos) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: more cells than the frame row count."
                    );
                }
                if (reader.remaining() == 0) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: a repeat run points past the last value."
                    );
                }
                final Object node = reader.decodeNext();
                Arrays.fill(out, pos, pos + (int) count, node);
                pos += (int) count;
            } else {
                // Unbound run
                final long count = len + 1;
                if (count > rows - pos) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: more cells than the frame row count."
                    );
                }
                Arrays.fill(out, pos, pos + (int) count, null);
                pos += (int) count;
            }
        }
        // Implicit tail: all remaining values, once each
        final int tail = reader.remaining();
        if (tail > rows - pos) {
            throw new RdfProtoDeserializationError("Corrupt column layout: more cells than the frame row count.");
        }
        reader.decodeInto(out, pos, tail);
        pos += tail;
        // The rest of the cells, up to the frame row count, are unbound
        Arrays.fill(out, pos, rows, null);
    }
}
