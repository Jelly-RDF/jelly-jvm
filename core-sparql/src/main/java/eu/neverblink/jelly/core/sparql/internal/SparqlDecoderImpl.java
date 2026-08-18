package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of SparqlDecoder.
 *
 * @param <TNode> the type of RDF nodes in the library
 * @param <TDatatype> the type of RDF datatypes in the library
 */
@ExperimentalApi
@InternalApi
public final class SparqlDecoderImpl<TNode, TDatatype> extends DecoderBase<TNode, TDatatype> implements SparqlDecoder {

    // Run lengths of 0–14 are inlined in the layout token. 15 needs an extension varint.
    private static final int MAX_INLINE_LEN = 15;

    private final SparqlResultsHandler<TNode> handler;
    private final SparqlResultsOptions supportedOptions;

    private SparqlResultsOptions currentOptions = null;
    private String[] variableNames = null;
    private int[] varToColumn = null;
    private TNode[] rowBuffer = null;
    // Per-variable column decode buffers, reused across frames. The inner arrays grow to the
    // largest row count seen so far.
    private Object[][] decodedColumns = null;
    private boolean askResultReceived = false;

    public SparqlDecoderImpl(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        SparqlResultsHandler<TNode> handler,
        SparqlResultsOptions supportedOptions
    ) {
        super(converter);
        this.handler = handler;
        this.supportedOptions =
            supportedOptions != null ? supportedOptions : JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS;
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
        }
        if (currentOptions == null) {
            throw new RdfProtoDeserializationError("Stream options were not received before the first frame content.");
        }
        if (frame.getAskResult() != null) {
            handleAskResult(frame);
            return;
        }
        if (!frame.getVariables().isEmpty()) {
            handleHeader(frame.getVariables());
        } else if (variableNames == null && frame.getOptions() != null && !askResultReceived) {
            // The first frame (the one carrying the options) with no variables:
            // a zero-variable result set.
            handleHeader(List.of());
        }
        if (variableNames == null) {
            throw new RdfProtoDeserializationError("The result set header (variables) was not received.");
        }

        // Apply all lookup entries before decoding any column. In a packed entry only the first
        // value states its id, the rest are sequential.
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
                getDatatypeLookup().update(id, converter.makeDatatype(value));
                id = 0;
            }
        }

        final int rows = frame.getRowCount();
        if (rows < 0) {
            throw new RdfProtoDeserializationError("Invalid row count (over 2^31).");
        }
        final var iriColumns = frame.getIriColumns();
        final var bnodeColumns = frame.getBnodeColumns();
        final var literalColumns = frame.getLiteralColumns();
        final var polyColumns = frame.getPolyColumns();
        final int iriEnd = iriColumns.size();
        final int bnodeEnd = iriEnd + bnodeColumns.size();
        final int literalEnd = bnodeEnd + literalColumns.size();
        final int totalColumns = literalEnd + polyColumns.size();
        if (totalColumns != variableNames.length) {
            throw new RdfProtoDeserializationError(
                "The frame has %d columns, but the header declares %d variables.".formatted(
                    totalColumns,
                    variableNames.length
                )
            );
        }

        // Decode each variable's column into a row-indexed array (reused across frames)
        for (int v = 0; v < variableNames.length; v++) {
            final int c = varToColumn[v];
            final Object[] out = decodeBufferForVariable(v, rows);
            if (c < iriEnd) {
                final SparqlIriColumn column = get(iriColumns, c);
                decodeColumn(new IriReader(column), column.getLayouts(), rows, out);
            } else if (c < bnodeEnd) {
                final SparqlBnodeColumn column = get(bnodeColumns, c - iriEnd);
                decodeColumn(new BnodeReader(column.getValues().iterator()), column.getLayouts(), rows, out);
            } else if (c < literalEnd) {
                final SparqlLiteralColumn column = get(literalColumns, c - bnodeEnd);
                decodeColumn(literalReader(column), column.getLayouts(), rows, out);
            } else {
                final SparqlPolyColumn column = get(polyColumns, c - literalEnd);
                decodeColumn(new PolyReader(column.getValues().iterator()), column.getLayouts(), rows, out);
            }
        }

        // Emit the rows
        final TNode[] row = rowBuffer;
        for (int r = 0; r < rows; r++) {
            for (int v = 0; v < row.length; v++) {
                //noinspection unchecked
                row[v] = (TNode) decodedColumns[v][r];
            }
            handler.handleRow(row);
        }
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
        if (currentOptions == null) {
            currentOptions = options;
        }
    }

    private void handleAskResult(SparqlResultsFrame frame) {
        if (askResultReceived) {
            throw new RdfProtoDeserializationError("Received more than one boolean (ASK) result.");
        }
        if (variableNames != null) {
            throw new RdfProtoDeserializationError("Unexpected boolean (ASK) result in a stream of bindings.");
        }
        if (
            frame.getRowCount() != 0 ||
            !frame.getVariables().isEmpty() ||
            !frame.getIriColumns().isEmpty() ||
            !frame.getBnodeColumns().isEmpty() ||
            !frame.getLiteralColumns().isEmpty() ||
            !frame.getPolyColumns().isEmpty()
        ) {
            throw new RdfProtoDeserializationError(
                "A frame with a boolean (ASK) result must not carry any bindings content."
            );
        }
        askResultReceived = true;
        handler.handleAskResult(frame.getAskResult().getValue());
    }

    private void handleHeader(Iterable<SparqlVariable> variables) {
        int count = 0;
        for (final var ignored : variables) {
            count++;
        }
        if (variableNames != null && count != variableNames.length) {
            throw new RdfProtoDeserializationError(
                "A restated header must declare the same variables as the original header."
            );
        }
        final String[] names = new String[count];
        final int[] columnIndices = new int[count];
        final boolean[] seen = new boolean[count];
        int i = 0;
        for (final SparqlVariable variable : variables) {
            names[i] = variable.getName();
            final int c = variable.getColumnIndex();
            if (c < 0 || c >= count || seen[c]) {
                throw new RdfProtoDeserializationError(
                    "Invalid column index %d for variable %s: the column indices must form a permutation of [0, %d).".formatted(
                        c,
                        names[i],
                        count
                    )
                );
            }
            seen[c] = true;
            columnIndices[i] = c;
            i++;
        }
        if (variableNames == null) {
            variableNames = names;
            rowBuffer = handler.createRowBuffer(count);
            if (rowBuffer == null || rowBuffer.length != count) {
                throw new RdfProtoDeserializationError("The handler's createRowBuffer returned an invalid buffer.");
            }
            decodedColumns = new Object[count][];
            handler.handleVariables(List.of(names));
        } else if (!Arrays.equals(variableNames, names)) {
            throw new RdfProtoDeserializationError(
                "A restated header must declare the same variables in the same order as the original header."
            );
        }
        varToColumn = columnIndices;
    }

    /**
     * Per-column state for resolving the prefix_id / name_id inference of RdfIri values,
     * in column order (see the sparql.proto comments).
     */
    private final class IriState {

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
            return getNameDecoder().decodeRaw(prefixId, nameId);
        }
    }

    /**
     * Decodes the run values of one column, in order. Each call decodes the next value.
     */
    private abstract static class ValueReader<TNode> {

        abstract boolean hasNext();

        abstract TNode decodeNext();
    }

    private final class IriReader extends ValueReader<TNode> {

        private final RepeatedInt nameIds;
        // Empty (every value has prefix id 0), one prefix for the whole column, or one entry
        // per value – in which case the RdfIri prefix inference applies along the list
        private final RepeatedInt prefixIds;
        private final IriState iriState = new IriState();
        private int index = 0;

        IriReader(SparqlIriColumn column) {
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
        boolean hasNext() {
            return index < nameIds.size();
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

        private final Iterator<String> values;

        BnodeReader(Iterator<String> values) {
            this.values = values;
        }

        @Override
        boolean hasNext() {
            return values.hasNext();
        }

        @Override
        TNode decodeNext() {
            return converter.makeBlankNode(values.next());
        }
    }

    /**
     * Picks the reader for a literal column: the lexical forms replace the RdfLiteral values
     * if present (see the sparql.proto comments).
     */
    private ValueReader<TNode> literalReader(SparqlLiteralColumn column) {
        if (column.getLexValues().isEmpty()) {
            if (column.getDatatype() != 0) {
                throw new RdfProtoDeserializationError(
                    "Corrupt literal column: a datatype is stated for a column with no lexical forms."
                );
            }
            return new LiteralReader(column.getValues().iterator());
        }
        if (!column.getValues().isEmpty()) {
            throw new RdfProtoDeserializationError(
                "Corrupt literal column: the column has both lexical forms and full literal values."
            );
        }
        return new LexLiteralReader(column);
    }

    private final class LiteralReader extends ValueReader<TNode> {

        private final Iterator<RdfLiteral> values;

        LiteralReader(Iterator<RdfLiteral> values) {
            this.values = values;
        }

        @Override
        boolean hasNext() {
            return values.hasNext();
        }

        @Override
        TNode decodeNext() {
            return convertLiteral(values.next());
        }
    }

    /**
     * Reader for a datatype-monomorphic literal column: the values are plain lexical forms and
     * the datatype (if any) is resolved once for the whole column.
     */
    private final class LexLiteralReader extends ValueReader<TNode> {

        private final RepeatedString values;
        // Null for a column of simple literals
        private final TDatatype datatype;
        private int index = 0;

        LexLiteralReader(SparqlLiteralColumn column) {
            this.values = column.getLexValues();
            final int datatypeId = column.getDatatype();
            this.datatype = datatypeId == 0 ? null : getDatatypeLookup().get(datatypeId);
        }

        @Override
        boolean hasNext() {
            return index < values.size();
        }

        @Override
        TNode decodeNext() {
            final String lex = values.get(index++);
            return datatype == null ? converter.makeSimpleLiteral(lex) : converter.makeDtLiteral(lex, datatype);
        }
    }

    private final class PolyReader extends ValueReader<TNode> {

        private final Iterator<SparqlTerm> values;
        private final IriState iriState = new IriState();

        PolyReader(Iterator<SparqlTerm> values) {
            this.values = values;
        }

        @Override
        boolean hasNext() {
            return values.hasNext();
        }

        @Override
        TNode decodeNext() {
            final SparqlTerm term = values.next();
            return switch (term.getTermFieldNumber()) {
                case SparqlTerm.IRI -> iriState.decode(term.getIri().getPrefixId(), term.getIri().getNameId());
                case SparqlTerm.BNODE -> converter.makeBlankNode(term.getBnode());
                case SparqlTerm.LITERAL -> convertLiteral(term.getLiteral());
                default -> throw new RdfProtoDeserializationError("A term in a polymorphic column has no value set.");
            };
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
            for (int j = 0; j < skip; j++) {
                if (!reader.hasNext()) {
                    throw new RdfProtoDeserializationError("Corrupt column layout: not enough values in the column.");
                }
                out[pos++] = reader.decodeNext();
            }
            if (kind == 0) {
                // Repeat run
                final long count = len + 2;
                if (count > rows - pos) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: more cells than the frame row count."
                    );
                }
                if (!reader.hasNext()) {
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
        while (reader.hasNext()) {
            if (pos >= rows) {
                throw new RdfProtoDeserializationError("Corrupt column layout: more cells than the frame row count.");
            }
            out[pos++] = reader.decodeNext();
        }
        // The rest of the cells, up to the frame row count, are unbound
        Arrays.fill(out, pos, rows, null);
    }
}
