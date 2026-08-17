package eu.neverblink.jelly.core.sparql.internal;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfDefaultGraph;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

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

    // Not a valid datatype lookup id – marks a literal column that cannot state one datatype
    private static final int MIXED_DATATYPES = -1;

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
            usedNames.mark(nameId);
            if (prefixId != 0) {
                usedPrefixes.mark(prefixId);
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
            final RdfIri raw = getNodeEncoder().makeIriRaw(iri);
            usedNames.mark(raw.getNameId());
            final int prefixId = raw.getPrefixId();
            if (prefixId != 0) {
                usedPrefixes.mark(prefixId);
            }
            return raw;
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
            usedDatatypes.mark(literal.getDatatype());
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

    // The lookup ids touched by the current frame – both the ids assigned by its lookup entries
    // and the ids its columns refer to.
    private final UsedIds usedNames;
    private final UsedIds usedPrefixes;
    private final UsedIds usedDatatypes;

    // Lookup entries collected for the current frame, packed into runs of consecutive ids
    private final PackedEntries<RdfNameEntryPacked.Mutable> nameEntries;
    private final PackedEntries<RdfPrefixEntryPacked.Mutable> prefixEntries;
    private final PackedEntries<RdfDatatypeEntryPacked.Mutable> datatypeEntries;

    /**
     * The set of ids of one lookup table touched by the current frame.
     * <p>
     * A frame's lookup entries are all applied before any of its columns, so an entry that the
     * frame overwrites while still referring to the old value cannot be represented. The lookups
     * evict the least recently used entry and everything this frame touched sits at the recent
     * end, so the frame stays safe exactly as long as it has not touched every id of the table –
     * which is what the count is for.
     */
    private static final class UsedIds {

        private final long[] bits;
        private final String kind;
        // Number of set bits
        private int count = 0;
        // Highest count that still leaves room for one more row. Set in setVariables.
        private int budget = 0;

        UsedIds(int tableSize, String kind) {
            this.bits = new long[(tableSize + 64) >> 6];
            this.kind = kind;
        }

        /**
         * Marks an id as used by this frame. Branchless, and called for every encoded term, so it
         * folds the "was it already set" answer into the counter instead of branching on it.
         */
        void mark(int id) {
            final int word = id >> 6;
            final int bit = id & 63;
            final long old = bits[word];
            bits[word] = old | (1L << bit);
            count += (int) ((old >>> bit) & 1) ^ 1;
        }

        boolean isSet(int id) {
            return (bits[id >> 6] & (1L << (id & 63))) != 0;
        }

        boolean isFull() {
            return count > budget;
        }

        void setVariableCount(int variables, int tableSize) {
            // A disabled table can never be referenced, so it must not constrain the frame size
            budget = tableSize == 0 ? Integer.MAX_VALUE : tableSize - variables;
        }

        void resetFrameState() {
            Arrays.fill(bits, 0);
            count = 0;
        }
    }

    /**
     * Collects the lookup entries of a frame, coalescing consecutively numbered entries into
     * packed messages. The identifier numbering runs across frames, but the packing does not:
     * every frame starts a new packed entry.
     *
     * @param <T> the packed entry message type
     */
    private static final class PackedEntries<T> {

        private final UsedIds used;
        private final IntFunction<T> factory;
        private final BiConsumer<T, String> adder;

        private final ArrayList<T> entries = new ArrayList<>();
        private T current = null;
        // State for resolving 0-compressed lookup entry identifiers
        private int lastAssignedId = 0;

        PackedEntries(UsedIds used, IntFunction<T> factory, BiConsumer<T, String> adder) {
            this.used = used;
            this.factory = factory;
            this.adder = adder;
        }

        void append(int entryId, String value) {
            final int id = entryId == 0 ? lastAssignedId + 1 : entryId;
            checkAndMarkUsed(used, id);
            if (current != null && id == lastAssignedId + 1) {
                // Continues the open run – the packed entry numbers it implicitly
                adder.accept(current, value);
            } else {
                // The id of a fresh entry is passed on as it came, so that a 0 keeps meaning
                // "continue from the previous frame"
                current = factory.apply(entryId);
                adder.accept(current, value);
                entries.add(current);
            }
            lastAssignedId = id;
        }

        void resetFrameState() {
            entries.clear();
            current = null;
        }
    }

    /**
     * Constructor.
     *
     * @param converter the converter to use
     * @param params parameters for the encoder
     */
    public SparqlEncoderImpl(ProtoEncoderConverter<TNode> converter, SparqlEncoder.Params params) {
        super(converter, params);
        usedNames = new UsedIds(options.getMaxNameTableSize(), "name");
        usedPrefixes = new UsedIds(options.getMaxPrefixTableSize(), "prefix");
        usedDatatypes = new UsedIds(options.getMaxDatatypeTableSize(), "datatype");
        nameEntries = new PackedEntries<>(
            usedNames,
            id -> RdfNameEntryPacked.newInstance().setId(id),
            RdfNameEntryPacked.Mutable::addValues
        );
        prefixEntries = new PackedEntries<>(
            usedPrefixes,
            id -> RdfPrefixEntryPacked.newInstance().setId(id),
            RdfPrefixEntryPacked.Mutable::addValues
        );
        datatypeEntries = new PackedEntries<>(
            usedDatatypes,
            id -> RdfDatatypeEntryPacked.newInstance().setId(id),
            RdfDatatypeEntryPacked.Mutable::addValues
        );
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
        final int n = columns.length;
        usedNames.setVariableCount(n, options.getMaxNameTableSize());
        usedPrefixes.setVariableCount(n, options.getMaxPrefixTableSize());
        usedDatatypes.setVariableCount(n, options.getMaxDatatypeTableSize());
    }

    @Override
    public boolean appendRow(TNode[] row) {
        if (columns == null) {
            throw new RdfProtoSerializationError("Variables must be set before appending rows.");
        }
        if (row.length != columns.length) {
            throw new RdfProtoSerializationError(
                "Expected %d bindings in the row, got %d.".formatted(columns.length, row.length)
            );
        }
        // A frame that already holds rows is ended rather than overfilled. An empty frame takes
        // the row whatever it costs – ending it again would not make any more room.
        if (rowCount > 0 && !hasRoomForAnotherRow()) {
            return false;
        }
        for (int i = 0; i < row.length; i++) {
            addCell(columns[i], row[i]);
        }
        rowCount++;
        return true;
    }

    /**
     * Whether one more row can be encoded without overflowing the frame. Checked before the row
     * is touched, so that a full frame can be ended and the row retried – once the lookups have
     * been mutated, neither is possible any more.
     */
    private boolean hasRoomForAnotherRow() {
        return (
            rowCount < MAX_ROWS_PER_FRAME && !usedNames.isFull() && !usedPrefixes.isFull() && !usedDatatypes.isFull()
        );
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
        for (final RdfNameEntryPacked.Mutable entry : nameEntries.entries) {
            frame.addNames(entry);
        }
        for (final RdfPrefixEntryPacked.Mutable entry : prefixEntries.entries) {
            frame.addPrefixes(entry);
        }
        for (final RdfDatatypeEntryPacked.Mutable entry : datatypeEntries.entries) {
            frame.addDatatypes(entry);
        }

        // Emit the columns grouped by type, in variable order within each group – the same
        // order in which the column indices were assigned.
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_IRI) {
                final SparqlIriColumn.Mutable column = SparqlIriColumn.newInstance();
                final RepeatedInt nameIds = RepeatedInt.newEmptyInstance();
                // The prefix ids keep the "same prefix as the previous IRI" inference of RdfIri.
                // If the whole column resolves to one prefix – the usual case – it is stated once
                // instead of once per value.
                final int[] prefixes = new int[columns[i].values.size()];
                int lastPrefix = 0;
                int columnPrefix = 0;
                boolean onePrefix = true;
                int index = 0;
                for (final Object value : columns[i].values) {
                    final RdfIri iri = (RdfIri) value;
                    nameIds.add(iri.getNameId());
                    final int prefix = iri.getPrefixId();
                    prefixes[index] = prefix;
                    // Resolve the inference to see whether the column really stays on one prefix
                    lastPrefix = prefix == 0 ? lastPrefix : prefix;
                    if (index == 0) {
                        columnPrefix = lastPrefix;
                    } else if (lastPrefix != columnPrefix) {
                        onePrefix = false;
                    }
                    index++;
                }
                column.setNameIds(nameIds);
                final RepeatedInt prefixIds = RepeatedInt.newEmptyInstance();
                if (!onePrefix) {
                    for (int j = 0; j < prefixes.length; j++) {
                        prefixIds.add(prefixes[j]);
                    }
                    column.setPrefixIds(prefixIds);
                } else if (columnPrefix != 0) {
                    prefixIds.add(columnPrefix);
                    column.setPrefixIds(prefixIds);
                }
                // Otherwise every value has prefix id 0, which the decoder assumes anyway
                column.setLayouts(copyLayout(columns[i].layout));
                frame.addIriColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_BNODE) {
                final SparqlBnodeColumn.Mutable column = SparqlBnodeColumn.newInstance();
                for (final Object value : columns[i].values) {
                    column.addValues((String) value);
                }
                column.setLayouts(copyLayout(columns[i].layout));
                frame.addBnodeColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            if (types[i] == TYPE_LITERAL) {
                final SparqlLiteralColumn.Mutable column = SparqlLiteralColumn.newInstance();
                final List<Object> values = columns[i].values;
                // If every value has the same datatype – the usual case – the column states it
                // once and carries only the lexical forms.
                final int datatype = commonDatatype(values);
                if (datatype == MIXED_DATATYPES) {
                    for (final Object value : values) {
                        column.addValues((RdfLiteral) value);
                    }
                } else {
                    for (final Object value : values) {
                        column.addLexValues(((RdfLiteral) value).getLex());
                    }
                    if (datatype != 0) {
                        column.setDatatype(datatype);
                    }
                    // Datatype 0 means simple literals, which the decoder assumes anyway
                }
                column.setLayouts(copyLayout(columns[i].layout));
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
                column.setLayouts(copyLayout(columns[i].layout));
                frame.addPolyColumns(column);
            }
        }

        // Reset the per-frame state
        for (final ColumnState col : columns) {
            col.resetFrameState();
        }
        nameEntries.resetFrameState();
        prefixEntries.resetFrameState();
        datatypeEntries.resetFrameState();
        usedNames.resetFrameState();
        usedPrefixes.resetFrameState();
        usedDatatypes.resetFrameState();
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

    /**
     * Returns the datatype id shared by all values of a literal column (0 for simple literals),
     * or MIXED_DATATYPES if the values have more than one datatype or any of them is
     * language-tagged. An empty column counts as simple literals – both forms are empty anyway.
     */
    private static int commonDatatype(List<Object> values) {
        int datatype = 0;
        boolean first = true;
        for (final Object value : values) {
            final RdfLiteral literal = (RdfLiteral) value;
            final int kind = literal.getLiteralKindFieldNumber();
            if (kind == RdfLiteral.LANGTAG) {
                return MIXED_DATATYPES;
            }
            // A simple literal has no literal kind set at all
            final int dt = kind == RdfLiteral.DATATYPE ? literal.getDatatype() : 0;
            if (first) {
                datatype = dt;
                first = false;
            } else if (dt != datatype) {
                return MIXED_DATATYPES;
            }
        }
        return datatype;
    }

    private static RepeatedInt copyLayout(RepeatedInt layout) {
        final RepeatedInt copy = RepeatedInt.newEmptyInstance();
        copy.addAll(layout);
        return copy;
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        nameEntries.append(nameEntry.getId(), nameEntry.getValue());
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        prefixEntries.append(prefixEntry.getId(), prefixEntry.getValue());
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        datatypeEntries.append(datatypeEntry.getId(), datatypeEntry.getValue());
    }

    @Override
    public RdfTriple appendQuotedTriple(TNode subject, TNode predicate, TNode object) {
        throw new RdfProtoSerializationError("RDF-star quoted triples are not supported in Jelly-SPARQL.");
    }

    /**
     * Registers a lookup entry assigned by the current frame. Assigning an id that the frame
     * already touched means the entry would be overwritten while the frame still refers to it.
     * <p>
     * appendRow ends a frame before it can get that far, so this can only fire for a single row
     * that does not fit in the lookup tables at all – no framing decision can help there.
     */
    private static void checkAndMarkUsed(UsedIds used, int id) {
        if (used.isSet(id)) {
            throw new RdfProtoSerializationError(
                (
                    "The %s lookup table is too small to encode a single row of these results: " +
                    "entry %d would be overwritten while still referenced in the current frame. " +
                    "Increase the max %s table size."
                ).formatted(used.kind, id, used.kind)
            );
        }
        used.mark(id);
    }
}
