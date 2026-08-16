package eu.neverblink.jelly.core.sparql.internal;

import static eu.neverblink.jelly.core.internal.BaseJellyOptions.*;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.DecoderBase;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.JellySparqlOptions;
import eu.neverblink.jelly.core.sparql.SparqlDecoder;
import eu.neverblink.jelly.core.sparql.SparqlResultsHandler;
import eu.neverblink.protoc.java.runtime.RepeatedLong;
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

    private final SparqlResultsHandler<TNode> handler;
    private final SparqlResultsOptions supportedOptions;

    private SparqlResultsOptions currentOptions = null;
    private String[] variableNames = null;
    private int[] varToColumn = null;
    private TNode[] rowBuffer = null;

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

    @Override
    protected int getNameTableSize() {
        return currentOptions == null ? SMALL_NAME_TABLE_SIZE : currentOptions.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        return currentOptions == null ? SMALL_PREFIX_TABLE_SIZE : currentOptions.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        return currentOptions == null ? SMALL_DT_TABLE_SIZE : currentOptions.getMaxDatatypeTableSize();
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
        if (!frame.getVariables().isEmpty()) {
            handleHeader(frame.getVariables());
        } else if (variableNames == null && frame.getOptions() != null) {
            // The first frame (the one carrying the options) with no variables:
            // a zero-variable result set.
            handleHeader(List.of());
        }
        if (variableNames == null) {
            throw new RdfProtoDeserializationError("The result set header (variables) was not received.");
        }

        // Apply all lookup entries before decoding any column
        for (final RdfNameEntry entry : frame.getNames()) {
            getNameDecoder().updateNames(entry);
        }
        for (final RdfPrefixEntry entry : frame.getPrefixes()) {
            getNameDecoder().updatePrefixes(entry);
        }
        for (final RdfDatatypeEntry entry : frame.getDatatypes()) {
            getDatatypeLookup().update(entry.getId(), converter.makeDatatype(entry.getValue()));
        }

        final int rows = frame.getRowCount();
        if (rows < 0) {
            throw new RdfProtoDeserializationError("Invalid negative row count.");
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

        // Decode each variable's column into a row-indexed array
        final Object[][] decoded = new Object[variableNames.length][];
        for (int v = 0; v < variableNames.length; v++) {
            final int c = varToColumn[v];
            if (c < iriEnd) {
                final SparqlIriColumn column = get(iriColumns, c);
                decoded[v] = decodeColumn(new IriReader(column.getValues().iterator()), column.getLayout(), rows);
            } else if (c < bnodeEnd) {
                final SparqlBnodeColumn column = get(bnodeColumns, c - iriEnd);
                decoded[v] = decodeColumn(new BnodeReader(column.getValues().iterator()), column.getLayout(), rows);
            } else if (c < literalEnd) {
                final SparqlLiteralColumn column = get(literalColumns, c - bnodeEnd);
                decoded[v] = decodeColumn(new LiteralReader(column.getValues().iterator()), column.getLayout(), rows);
            } else {
                final SparqlPolyColumn column = get(polyColumns, c - literalEnd);
                decoded[v] = decodeColumn(new PolyReader(column.getValues().iterator()), column.getLayout(), rows);
            }
        }

        // Emit the rows
        final TNode[] row = rowBuffer;
        for (int r = 0; r < rows; r++) {
            for (int v = 0; v < decoded.length; v++) {
                //noinspection unchecked
                row[v] = (TNode) decoded[v][r];
            }
            handler.handleRow(row);
        }
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
            handler.handleVariables(List.of(names));
        } else if (!Arrays.equals(variableNames, names)) {
            throw new RdfProtoDeserializationError(
                "A restated header must declare the same variables in the same order as the original header."
            );
        }
        varToColumn = columnIndices;
    }

    /**
     * Decodes the run values of one column, in order. Each call decodes the next value.
     */
    private abstract class ValueReader {

        abstract boolean hasNext();

        abstract TNode decodeNext();
    }

    private final class IriReader extends ValueReader {

        private final Iterator<RdfIri> values;
        private int lastPrefixId = 0;
        private int lastNameId = 0;

        IriReader(Iterator<RdfIri> values) {
            this.values = values;
        }

        @Override
        boolean hasNext() {
            return values.hasNext();
        }

        @Override
        TNode decodeNext() {
            return decodeIri(values.next());
        }

        TNode decodeIri(RdfIri iri) {
            int prefixId = iri.getPrefixId();
            if (prefixId == 0) {
                prefixId = lastPrefixId;
            } else {
                lastPrefixId = prefixId;
            }
            int nameId = iri.getNameId();
            if (nameId == 0) {
                nameId = lastNameId + 1;
            }
            lastNameId = nameId;
            return getNameDecoder().decodeRaw(prefixId, nameId);
        }
    }

    private final class BnodeReader extends ValueReader {

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

    private final class LiteralReader extends ValueReader {

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

    private final class PolyReader extends ValueReader {

        private final Iterator<SparqlTerm> values;
        private final IriReader iriState = new IriReader(null);

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
                case SparqlTerm.IRI -> iriState.decodeIri(term.getIri());
                case SparqlTerm.BNODE -> converter.makeBlankNode(term.getBnode());
                case SparqlTerm.LITERAL -> convertLiteral(term.getLiteral());
                default -> throw new RdfProtoDeserializationError("A term in a polymorphic column has no value set.");
            };
        }
    }

    /**
     * Decodes one column: walks the sequence layout, materializing the cells of the column.
     * Cells past the encoded sequence, up to the frame row count, are unbound (nulls).
     */
    private Object[] decodeColumn(ValueReader reader, RepeatedLong layout, int rows) {
        final Object[] out = new Object[rows];
        int pos = 0;
        final int layoutSize = layout.size();
        for (int k = 0; k < layoutSize; k++) {
            final long token = layout.get(k);
            final long skip = token >>> 4;
            final long kind = (token >> 3) & 1;
            long len = token & 7;
            if (len == 7) {
                k++;
                if (k >= layoutSize) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: an escaped length token is not followed by an extension."
                    );
                }
                len = 7 + layout.get(k);
                if (len < 0) {
                    // Overflow of the uint64 extension
                    throw new RdfProtoDeserializationError("Corrupt column layout: run length out of range.");
                }
            }
            if (skip > rows - pos) {
                throw new RdfProtoDeserializationError("Corrupt column layout: more cells than the frame row count.");
            }
            for (long j = 0; j < skip; j++) {
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
                for (long j = 0; j < count; j++) {
                    out[pos++] = node;
                }
            } else {
                // Unbound run: the cells are already nulls, just skip over them
                final long count = len + 1;
                if (count > rows - pos) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: more cells than the frame row count."
                    );
                }
                pos += count;
            }
        }
        // Implicit tail: all remaining values, once each
        while (reader.hasNext()) {
            if (pos >= rows) {
                throw new RdfProtoDeserializationError("Corrupt column layout: more cells than the frame row count.");
            }
            out[pos++] = reader.decodeNext();
        }
        // The rest of the cells (pos..rows) stay unbound
        return out;
    }
}
