package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfDefaultGraph;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of SparqlEncoder.
 * <p>
 * Builds the columnar frames: runs of equal consecutive values and unbound cells are
 * compressed into the layout field, values are grouped into per-type columns, and variables
 * whose values mix term types are moved to polymorphic columns (restating the header).
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@ExperimentalApi
@InternalApi
public final class SparqlEncoderImpl<TNode> extends SparqlEncoder<TNode> {

    private static final byte TYPE_UNSET = 0;
    private static final byte TYPE_IRI = 1;
    private static final byte TYPE_BNODE = 2;
    private static final byte TYPE_LITERAL = 3;
    private static final byte TYPE_POLY = 4;

    private static final int KIND_REPEAT = 0;
    private static final int KIND_UNBOUND = 1;

    // The layout tokens are uint32 with skip in the upper 27 bits, so a frame can hold at most
    // 2^27 - 1 rows (see the layout encoding notes in sparql.proto).
    private static final int MAX_ROWS_PER_FRAME = (1 << 27) - 1;

    // Run lengths of 0–14 are inlined in the token; 15 escapes to an extension varint.
    private static final int MAX_INLINE_LEN = 15;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    private static final RdfIri ZERO_IRI = RdfIri.newInstance();

    private static final class ColumnState {

        // Column type; sticky once set, only ever escalated to TYPE_POLY.
        byte type = TYPE_UNSET;

        // Run-length state
        Object runNode = null;
        int runLength = 0;
        boolean runIsUnbound = false;
        boolean runActive = false;
        // Number of values emitted exactly once since the last layout exception
        int skip = 0;

        // Per-frame, per-column IRI inference state.
        // lastPrefixId = -1 forces the first IRI of a column to carry its prefix id.
        int lastPrefixId = -1;
        int lastNameId = 0;

        // Encoded run values of the current frame (RdfIri, String or RdfLiteral)
        final ArrayList<Object> values = new ArrayList<>();
        // Layout of the current frame
        final RepeatedInt layout = RepeatedInt.newEmptyInstance();

        void resetFrameState() {
            runNode = null;
            runLength = 0;
            runActive = false;
            runIsUnbound = false;
            skip = 0;
            lastPrefixId = -1;
            lastNameId = 0;
            values.clear();
            layout.clear();
        }
    }

    /**
     * NodeEncoder passed to the converter: same as the underlying encoder, except that IRIs
     * are compressed against the current column's inference state, and all lookup references
     * are tracked for the frame working-set check.
     */
    private final class ColumnNodeEncoder implements NodeEncoder<TNode> {

        @Override
        public RdfIri makeIri(String iri) {
            final RdfIri raw = getNodeEncoder().makeIriRaw(iri);
            final int prefixId = raw.getPrefixId();
            final int nameId = raw.getNameId();
            markUsed(usedNames, nameId);
            if (prefixId != 0) {
                markUsed(usedPrefixes, prefixId);
            }
            final ColumnState col = currentColumn;
            final boolean samePrefix = prefixId == col.lastPrefixId;
            final boolean nextName = nameId == col.lastNameId + 1;
            col.lastPrefixId = prefixId;
            col.lastNameId = nameId;
            if (samePrefix) {
                if (nextName) {
                    return ZERO_IRI;
                }
                return RdfIri.newInstance().setNameId(nameId);
            } else if (nextName) {
                return RdfIri.newInstance().setPrefixId(prefixId);
            }
            return raw;
        }

        @Override
        public RdfIri makeIriRaw(String iri) {
            return getNodeEncoder().makeIriRaw(iri);
        }

        @Override
        public String makeBlankNode(String label) {
            return getNodeEncoder().makeBlankNode(label);
        }

        @Override
        public RdfLiteral makeSimpleLiteral(String lex) {
            return getNodeEncoder().makeSimpleLiteral(lex);
        }

        @Override
        public RdfLiteral makeLangLiteral(TNode lit, String lex, String lang) {
            return getNodeEncoder().makeLangLiteral(lit, lex, lang);
        }

        @Override
        public RdfLiteral makeDtLiteral(TNode lit, String lex, String dt) {
            final RdfLiteral literal = getNodeEncoder().makeDtLiteral(lit, lex, dt);
            markUsed(usedDatatypes, literal.getDatatype());
            return literal;
        }

        @Override
        public RdfTriple makeQuotedTriple(TNode s, TNode p, TNode o) {
            throw new RdfProtoSerializationError("RDF-star quoted triples are not supported in Jelly-SPARQL.");
        }

        @Override
        public RdfDefaultGraph makeDefaultGraph() {
            throw new RdfProtoSerializationError("The default graph is not a valid SPARQL result binding.");
        }
    }

    private String[] variableNames = null;
    private ColumnState[] columns = null;
    private byte[] lastEmittedTypes = null;
    private int rowCount = 0;
    private boolean firstFrame = true;

    private ColumnState currentColumn = null;
    private final ColumnNodeEncoder columnNodeEncoder = new ColumnNodeEncoder();

    // Lookup entries collected for the current frame
    private final ArrayList<RdfNameEntry> nameEntries = new ArrayList<>();
    private final ArrayList<RdfPrefixEntry> prefixEntries = new ArrayList<>();
    private final ArrayList<RdfDatatypeEntry> datatypeEntries = new ArrayList<>();

    // Bitsets tracking the lookup ids touched by the current frame – both the ids assigned by
    // its lookup entries and the ids its columns refer to. Used to detect when a lookup entry
    // would be overwritten while still referenced by this frame, which cannot be represented in
    // the columnar layout (all lookup entries of a frame are applied before any of its columns).
    private final long[] usedNames;
    private final long[] usedPrefixes;
    private final long[] usedDatatypes;

    // State for resolving 0-compressed lookup entry identifiers
    private int lastAssignedNameId = 0;
    private int lastAssignedPrefixId = 0;
    private int lastAssignedDatatypeId = 0;

    /**
     * Constructor.
     *
     * @param converter the converter to use
     * @param params parameters for the encoder
     */
    public SparqlEncoderImpl(ProtoEncoderConverter<TNode> converter, SparqlEncoder.Params params) {
        super(converter, params);
        usedNames = newBitset(options.getMaxNameTableSize());
        usedPrefixes = newBitset(options.getMaxPrefixTableSize());
        usedDatatypes = newBitset(options.getMaxDatatypeTableSize());
    }

    @Override
    public void setVariables(List<String> variables) {
        if (variableNames != null) {
            throw new RdfProtoSerializationError("Variables have already been set.");
        }
        variableNames = variables.toArray(String[]::new);
        columns = new ColumnState[variableNames.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = new ColumnState();
        }
    }

    @Override
    public void appendRow(TNode[] row) {
        if (columns == null) {
            throw new RdfProtoSerializationError("Variables must be set before appending rows.");
        }
        if (row.length != columns.length) {
            throw new RdfProtoSerializationError(
                "Expected %d bindings in the row, got %d.".formatted(columns.length, row.length)
            );
        }
        if (rowCount >= MAX_ROWS_PER_FRAME) {
            throw new RdfProtoSerializationError(
                "A single frame cannot hold more than %d rows. Call endFrame() more often.".formatted(
                    MAX_ROWS_PER_FRAME
                )
            );
        }
        for (int i = 0; i < row.length; i++) {
            addCell(columns[i], row[i]);
        }
        rowCount++;
    }

    @Override
    public SparqlResultsFrame endFrame() {
        if (columns == null) {
            throw new RdfProtoSerializationError("Variables must be set before ending a frame.");
        }
        for (final ColumnState col : columns) {
            if (col.runActive && col.runIsUnbound) {
                // Trailing unbound run – omitted, the decoder pads with unbound cells.
                col.runActive = false;
            } else {
                finalizeRun(col);
            }
        }

        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance();
        if (firstFrame) {
            frame.setOptions(options);
        }

        // Effective column types for this frame: never-bound columns are emitted as IRI columns.
        final byte[] types = new byte[columns.length];
        for (int i = 0; i < columns.length; i++) {
            types[i] = columns[i].type == TYPE_UNSET ? TYPE_IRI : columns[i].type;
        }
        if (firstFrame || !Arrays.equals(types, lastEmittedTypes)) {
            // Emit (or restate) the header
            final int[] columnIndices = new int[columns.length];
            int nextIndex = 0;
            for (byte type = TYPE_IRI; type <= TYPE_POLY; type++) {
                for (int i = 0; i < types.length; i++) {
                    if (types[i] == type) {
                        columnIndices[i] = nextIndex++;
                    }
                }
            }
            for (int i = 0; i < variableNames.length; i++) {
                frame.addVariables(
                    SparqlVariable.newInstance().setName(variableNames[i]).setColumnIndex(columnIndices[i])
                );
            }
            lastEmittedTypes = types;
        }

        frame.setRowCount(rowCount);
        for (final RdfNameEntry entry : nameEntries) {
            frame.addNames(entry);
        }
        for (final RdfPrefixEntry entry : prefixEntries) {
            frame.addPrefixes(entry);
        }
        for (final RdfDatatypeEntry entry : datatypeEntries) {
            frame.addDatatypes(entry);
        }

        // Emit the columns grouped by type, in variable order within each group – the same
        // order in which the column indices were assigned.
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_IRI) {
                final SparqlIriColumn.Mutable column = SparqlIriColumn.newInstance();
                for (final Object value : columns[i].values) {
                    column.addValues((RdfIri) value);
                }
                column.setLayout(copyLayout(columns[i].layout));
                frame.addIriColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_BNODE) {
                final SparqlBnodeColumn.Mutable column = SparqlBnodeColumn.newInstance();
                for (final Object value : columns[i].values) {
                    column.addValues((String) value);
                }
                column.setLayout(copyLayout(columns[i].layout));
                frame.addBnodeColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_LITERAL) {
                final SparqlLiteralColumn.Mutable column = SparqlLiteralColumn.newInstance();
                for (final Object value : columns[i].values) {
                    column.addValues((RdfLiteral) value);
                }
                column.setLayout(copyLayout(columns[i].layout));
                frame.addLiteralColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_POLY) {
                final SparqlPolyColumn.Mutable column = SparqlPolyColumn.newInstance();
                for (final Object value : columns[i].values) {
                    final SparqlTerm.Mutable term = SparqlTerm.newInstance();
                    if (value instanceof RdfIri iri) {
                        term.setIri(iri);
                    } else if (value instanceof String bnode) {
                        term.setBnode(bnode);
                    } else {
                        term.setLiteral((RdfLiteral) value);
                    }
                    column.addValues(term);
                }
                column.setLayout(copyLayout(columns[i].layout));
                frame.addPolyColumns(column);
            }
        }

        // Reset the per-frame state
        for (final ColumnState col : columns) {
            col.resetFrameState();
        }
        nameEntries.clear();
        prefixEntries.clear();
        datatypeEntries.clear();
        Arrays.fill(usedNames, 0);
        Arrays.fill(usedPrefixes, 0);
        Arrays.fill(usedDatatypes, 0);
        rowCount = 0;
        firstFrame = false;

        // Pre-calculate the serialized size, while all objects are likely still in cache.
        frame.getSerializedSize();
        return frame;
    }

    private void addCell(ColumnState col, TNode node) {
        if (node == null) {
            if (col.runActive && col.runIsUnbound) {
                col.runLength++;
                return;
            }
            finalizeRun(col);
            col.runActive = true;
            col.runIsUnbound = true;
            col.runLength = 1;
            col.runNode = null;
            return;
        }
        if (col.runActive && !col.runIsUnbound && node.equals(col.runNode)) {
            col.runLength++;
            return;
        }
        finalizeRun(col);
        col.runActive = true;
        col.runIsUnbound = false;
        col.runLength = 1;
        col.runNode = node;
        encodeValue(col, node);
    }

    private void encodeValue(ColumnState col, TNode node) {
        currentColumn = col;
        final Object encoded = converter.nodeToProto(columnNodeEncoder, node);
        final byte valueType;
        if (encoded instanceof RdfIri) {
            valueType = TYPE_IRI;
        } else if (encoded instanceof String) {
            valueType = TYPE_BNODE;
        } else if (encoded instanceof RdfLiteral) {
            valueType = TYPE_LITERAL;
        } else {
            throw new RdfProtoSerializationError(
                "Unsupported term type in SPARQL results: %s".formatted(
                    encoded == null ? "null" : encoded.getClass().getName()
                )
            );
        }
        if (col.type == TYPE_UNSET) {
            col.type = valueType;
        } else if (col.type != TYPE_POLY && col.type != valueType) {
            col.type = TYPE_POLY;
        }
        col.values.add(encoded);
    }

    private void finalizeRun(ColumnState col) {
        if (!col.runActive) {
            return;
        }
        col.runActive = false;
        if (col.runIsUnbound) {
            emitException(col, KIND_UNBOUND, col.runLength - 1);
        } else if (col.runLength >= 2) {
            emitException(col, KIND_REPEAT, col.runLength - 2);
        } else {
            col.skip++;
        }
    }

    private void emitException(ColumnState col, int kind, int len) {
        final int lenCode = Math.min(len, MAX_INLINE_LEN);
        col.layout.add((col.skip << 5) | (kind << 4) | lenCode);
        if (lenCode == MAX_INLINE_LEN) {
            col.layout.add(len - MAX_INLINE_LEN);
        }
        col.skip = 0;
    }

    private static RepeatedInt copyLayout(RepeatedInt layout) {
        final RepeatedInt copy = RepeatedInt.newEmptyInstance();
        copy.addAll(layout);
        return copy;
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        final int id = nameEntry.getId() == 0 ? lastAssignedNameId + 1 : nameEntry.getId();
        lastAssignedNameId = id;
        checkAndMarkUsed(usedNames, id, "name");
        nameEntries.add(nameEntry);
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        final int id = prefixEntry.getId() == 0 ? lastAssignedPrefixId + 1 : prefixEntry.getId();
        lastAssignedPrefixId = id;
        checkAndMarkUsed(usedPrefixes, id, "prefix");
        prefixEntries.add(prefixEntry);
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        final int id = datatypeEntry.getId() == 0 ? lastAssignedDatatypeId + 1 : datatypeEntry.getId();
        lastAssignedDatatypeId = id;
        checkAndMarkUsed(usedDatatypes, id, "datatype");
        datatypeEntries.add(datatypeEntry);
    }

    @Override
    public RdfTriple appendQuotedTriple(TNode subject, TNode predicate, TNode object) {
        throw new RdfProtoSerializationError("RDF-star quoted triples are not supported in Jelly-SPARQL.");
    }

    /**
     * Registers a lookup entry assigned by the current frame. Assigning an id that the frame
     * already touched means the entry would be overwritten while the frame still refers to it.
     */
    private void checkAndMarkUsed(long[] used, int id, String kind) {
        if (isSet(used, id)) {
            throw new RdfProtoSerializationError(
                (
                    "The %s lookup table is too small to encode this batch of results: entry %d " +
                    "would be overwritten while still referenced in the current frame. Increase the " +
                    "max %s table size or reduce the number of rows per frame."
                ).formatted(kind, id, kind)
            );
        }
        markUsed(used, id);
    }

    private static long[] newBitset(int tableSize) {
        return new long[(tableSize + 64) >> 6];
    }

    private static void markUsed(long[] bits, int id) {
        bits[id >> 6] |= 1L << (id & 63);
    }

    private static boolean isSet(long[] bits, int id) {
        return (bits[id >> 6] & (1L << (id & 63))) != 0;
    }
}
