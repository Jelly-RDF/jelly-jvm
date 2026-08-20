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
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.SparqlEncoder;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

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
public final class SparqlEncoderImpl<TNode> extends SparqlEncoder<TNode> implements NodeEncoder<TNode> {

    private static final byte TYPE_UNSET = 0;
    private static final byte TYPE_IRI = 1;
    private static final byte TYPE_BNODE = 2;
    private static final byte TYPE_LITERAL = 3;
    private static final byte TYPE_POLY = 4;

    private static final int KIND_REPEAT = 0;
    private static final int KIND_UNBOUND = 1;

    // The layout tokens are uint32 with skip in the upper 27 bits.
    private static final int MAX_ROWS_PER_FRAME = (1 << 27) - 1;

    // Run lengths of 0–14 are inlined in the token. 15 uses an extension varint.
    private static final int MAX_INLINE_LEN = 15;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    private static final RdfIri ZERO_IRI = RdfIri.newInstance();

    // Used as a type marker – the literal's parts go into the column buffers,
    // not into the returned proto message.
    private static final RdfLiteral LITERAL_MARKER = RdfLiteral.newInstance();

    // Not a valid datatype lookup id – marks a literal column that cannot state one datatype
    private static final int MIXED_DATATYPES = -1;
    // The column datatype of a column that has no literals yet
    private static final int DATATYPE_NONE = -2;
    // Per-value marker in litDatatypes for a language-tagged literal
    private static final int LANG_LITERAL = -1;

    /**
     * Temporary column state, filled in from beginFrame() through to endFrame().
     */
    private static final class ColumnState {

        // Column type. Sticky once set, only ever escalated to TYPE_POLY.
        byte type = TYPE_UNSET;
        // The effective type this column had in the last emitted header.
        // This value is kept across frame resets.
        byte lastEmittedType = TYPE_UNSET;

        // Run-length state. A run is active while runLength > 0. runNode == null then means
        // a run of unbound cells. Whenever runLength is 0, runNode is null too.
        Object runNode = null;
        int runLength = 0;
        // Number of values emitted exactly once since the last layout exception
        int skip = 0;

        // Per-frame, per-column IRI inference state. The prefix side of the inference is
        // resolved at frame end, from the aux ids.
        int lastNameId = 0;

        // Datatype shared by all literals of the column so far. One of: a positive lookup id,
        // 0 for simple literals, DATATYPE_NONE before the first literal, or MIXED_DATATYPES.
        int columnDatatype = DATATYPE_NONE;

        // Term type (TYPE_IRI/BNODE/LITERAL) of each encoded value of the frame, in order.
        // Only read back for polymorphic columns – the values themselves sit in the per-type
        // buffers below, and a mono-typed column reads its buffer directly. This is only
        // filled in while the column is polymorphic. A column that becomes polymorphic mid-frame
        // backfills what it skipped (see backfillTags).
        byte[] tags = new byte[0];
        int valueCount = 0;

        // Layout of the current frame
        final RepeatedInt layout = RepeatedInt.newEmptyInstance();

        // Name ids of the frame's IRI values, with the next-name inference already applied
        // (0 means "previous + 1").
        final RepeatedInt nameIds = RepeatedInt.newEmptyInstance();
        // One auxiliary int per IRI or literal value, in value order: the uncompressed prefix
        // id of an IRI, or the datatype lookup id of a literal (0 for a simple literal,
        // LANG_LITERAL for a language-tagged one). Bnodes add nothing.
        final RepeatedInt auxIds = RepeatedInt.newEmptyInstance();
        // Bnode labels, literal lexical forms and language tags (right after their lexical
        // form), appended at encode time in value order.
        final RepeatedString strings = RepeatedString.newEmptyInstance();

        // Lazily created – see PolyBuffers
        private PolyBuffers poly = null;

        PolyBuffers poly() {
            if (poly == null) {
                poly = new PolyBuffers();
            }
            return poly;
        }

        void addValueTag(byte tag) {
            // This is only needed for polymorphic columns.
            if (type == TYPE_POLY) {
                if (valueCount == tags.length) {
                    tags = Arrays.copyOf(tags, Math.max(16, tags.length * 2));
                }
                tags[valueCount] = tag;
            }
            valueCount++;
        }

        /**
         * Records the type of the values encoded before this column turned polymorphic.
         */
        void backfillTags(byte previousType) {
            if (valueCount > tags.length) {
                tags = new byte[Math.max(16, valueCount * 2)];
            }
            Arrays.fill(tags, 0, valueCount, previousType);
        }

        void resetFrameState() {
            runNode = null;
            runLength = 0;
            skip = 0;
            lastNameId = 0;
            columnDatatype = DATATYPE_NONE;
            valueCount = 0;
            layout.clear();
            nameIds.clear();
            auxIds.clear();
            strings.clear();
            if (poly != null) {
                poly.resetFrameState();
            }
        }
    }

    @Override
    public RdfIri makeIri(String iri) {
        // Get the ids directly without RdfIri allocation.
        final long ids = getLookupEncoder().makeIriIds(iri);
        final int nameId = (int) (ids >>> 32);
        final ColumnState col = currentColumn;
        appendIriIds(col, nameId == col.lastNameId + 1 ? 0 : nameId, (int) ids, nameId);
        return ZERO_IRI;
    }

    @Override
    public RdfIri makeIriRaw(String iri) {
        final long ids = getLookupEncoder().makeIriIds(iri);
        // Raw means no next-name inference, so the name id is stored as it is. The decoder
        // still tracks it as the new "previous name", hence the lastNameId update.
        final int nameId = (int) (ids >>> 32);
        appendIriIds(currentColumn, nameId, (int) ids, nameId);
        return ZERO_IRI;
    }

    private void appendIriIds(ColumnState col, int storedNameId, int prefixId, int nameId) {
        markUsed(usedNames, nameId);
        if (prefixId != 0) {
            markUsed(usedPrefixes, prefixId);
        }
        col.nameIds.add(storedNameId);
        col.auxIds.add(prefixId);
        col.lastNameId = nameId;
    }

    @Override
    public String makeBlankNode(String label) {
        final String bnode = getLookupEncoder().makeBlankNode(label);
        currentColumn.strings.add(bnode);
        return bnode;
    }

    @Override
    public RdfLiteral makeSimpleLiteral(String lex) {
        // No lookup table involved – the underlying encoder (and its cache) is skipped.
        // The reasoning here is that literals in SPARQL results repeat rarely anyway,
        // so this saves quite a lot of cache thrashing. Also, the cost of encoding a literal
        // here is much smaller than in RDF (no allocations).
        final ColumnState col = currentColumn;
        col.strings.add(lex);
        col.auxIds.add(0);
        trackColumnDatatype(col, 0);
        return LITERAL_MARKER;
    }

    @Override
    public RdfLiteral makeLangLiteral(TNode lit, String lex, String lang) {
        final ColumnState col = currentColumn;
        col.strings.add(lex);
        col.strings.add(lang);
        col.auxIds.add(LANG_LITERAL);
        // A language-tagged literal always forces the per-value literal representation
        col.columnDatatype = MIXED_DATATYPES;
        return LITERAL_MARKER;
    }

    @Override
    public RdfLiteral makeDtLiteral(TNode lit, String lex, String dt) {
        // The underlying encoder is still consulted for the datatype lookup id (and the
        // lookup entry emission that comes with it)
        final RdfLiteral literal = getLookupEncoder().makeDtLiteral(lit, lex, dt);
        final int datatype = literal.getDatatype();
        markUsed(usedDatatypes, datatype);
        final ColumnState col = currentColumn;
        col.strings.add(lex);
        col.auxIds.add(datatype);
        trackColumnDatatype(col, datatype);
        return LITERAL_MARKER;
    }

    private void trackColumnDatatype(ColumnState col, int datatype) {
        if (col.columnDatatype == DATATYPE_NONE) {
            col.columnDatatype = datatype;
        } else if (col.columnDatatype != datatype) {
            col.columnDatatype = MIXED_DATATYPES;
        }
    }

    @Override
    public RdfTriple makeQuotedTriple(TNode s, TNode p, TNode o) {
        throw new RdfProtoSerializationError("Triple terms are not supported in Jelly-SPARQL.");
    }

    @Override
    public RdfDefaultGraph makeDefaultGraph() {
        throw new RdfProtoSerializationError("The default graph is not a valid SPARQL result binding.");
    }

    private String[] variableNames = null;
    private ColumnState[] columns = null;
    private int rowCount = 0;
    private boolean firstFrame = true;

    private ColumnState currentColumn = null;

    // True while the buffers still hold the contents of the frame endFrame last returned.
    private boolean framePending = false;

    /**
     * The lookup ids used by the current frame – both the ids assigned by
     * its lookup entries and the ids its columns refer to. One bit per id, starting at
     * slot 1. Slot 0 is the remaining-budget counter: it starts at the table's budget,
     * every fresh mark decrements it, and a negative value means the frame has no room
     * for another row.
     * <p>
     * A frame's lookup entries are all applied before any of its columns, so an entry that
     * the frame overwrites while still referring to the old value cannot be represented. The
     * lookups evict the least recently used entry and everything this frame touched sits at
     * the recent end, so the frame stays safe exactly as long as it has not touched every id
     * of the table. The budget is set so that one more row of fresh ids still fits: the
     * table size minus one potential id per variable. Note that this will not work for triple terms,
     * but that's a future problem...
     */
    private final long[] usedNames;
    private final long[] usedPrefixes;
    private final long[] usedDatatypes;

    // Lookup entries collected for the current frame, packed into runs of consecutive ids
    private final PackedEntries nameEntries = new PackedEntries();
    private final PackedEntries prefixEntries = new PackedEntries();
    private final PackedEntries datatypeEntries = new PackedEntries();

    // Bit words for 1-based ids up to tableSize, plus the counter slot at index 0
    private static long[] newUsedIds(int tableSize) {
        return new long[1 + ((tableSize + 64) >> 6)];
    }

    /**
     * Marks an id as used by this frame. Branchless.
     */
    private static void markUsed(long[] used, int id) {
        final int word = 1 + (id >> 6);
        final int bit = id & 63;
        final long old = used[word];
        used[word] = old | (1L << bit);
        used[0] -= (~old >>> bit) & 1L;
    }

    private static boolean isUsed(long[] used, int id) {
        return (used[1 + (id >> 6)] & (1L << (id & 63))) != 0;
    }

    private static long usedIdsBudget(int tableSize, int variables) {
        // If table size is 0, then it's not used (does not constrain us)
        return tableSize == 0 ? Long.MAX_VALUE : tableSize - variables;
    }

    /**
     * Collects the lookup entries of a frame, coalescing consecutively numbered entries into
     * packed messages. The identifier numbering runs across frames, but the packing does not:
     * every frame starts a new packed entry.
     */
    private static final class PackedEntries
        extends AbstractCollection<RdfLookupEntryPacked>
        implements MessageCollection<RdfLookupEntryPacked, RdfLookupEntryPacked.Mutable>
    {

        // Pooled packed entry messages, reused across frames.
        private RdfLookupEntryPacked.Mutable[] entries = new RdfLookupEntryPacked.Mutable[0];
        private int size = 0;
        // The entry of the currently open run, kept in a
        // field so that continuing a run does not go through the array
        private RdfLookupEntryPacked.Mutable current = null;
        // State for resolving 0-compressed lookup entry identifiers
        private int lastAssignedId = 0;

        void append(long[] used, String kind, int entryId, String value) {
            final int id = entryId == 0 ? lastAssignedId + 1 : entryId;
            checkAndMarkUsed(used, id, kind);
            if (current != null && id == lastAssignedId + 1) {
                current.addValues(value);
            } else {
                current = appendMessage().setId(entryId);
                current.addValues(value);
            }
            lastAssignedId = id;
        }

        @Override
        public RdfLookupEntryPacked.Mutable appendMessage() {
            if (size == entries.length) {
                entries = Arrays.copyOf(entries, Math.max(8, entries.length * 2));
            }
            RdfLookupEntryPacked.Mutable entry = entries[size];
            if (entry == null) {
                entry = RdfLookupEntryPacked.newInstance();
                entries[size] = entry;
            } else {
                // Same reuse rule as the frame buffers: clear resets the previous frame's
                // values and the cached serialized size
                entry.clear();
            }
            size++;
            return entry;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void clear() {
            // Keeps the messages around for the next frame
            size = 0;
            current = null;
        }

        @Override
        public Iterator<RdfLookupEntryPacked> iterator() {
            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < size;
                }

                @Override
                public RdfLookupEntryPacked next() {
                    if (index >= size) {
                        throw new NoSuchElementException();
                    }
                    return entries[index++];
                }
            };
        }
    }

    /**
     * @param converter the converter to use
     * @param params parameters for the encoder
     */
    public SparqlEncoderImpl(ProtoEncoderConverter<TNode> converter, SparqlEncoder.Params params) {
        super(converter, params);
        usedNames = newUsedIds(options.getMaxNameTableSize());
        usedPrefixes = newUsedIds(options.getMaxPrefixTableSize());
        usedDatatypes = newUsedIds(options.getMaxDatatypeTableSize());
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
        resetUsedIds();
    }

    /** Clears the used-ids bits and puts each remaining-budget counter back at its budget. */
    private void resetUsedIds() {
        final int n = columns.length;
        resetUsedIds(usedNames, usedIdsBudget(options.getMaxNameTableSize(), n));
        resetUsedIds(usedPrefixes, usedIdsBudget(options.getMaxPrefixTableSize(), n));
        resetUsedIds(usedDatatypes, usedIdsBudget(options.getMaxDatatypeTableSize(), n));
    }

    private static void resetUsedIds(long[] used, long budget) {
        Arrays.fill(used, 0L);
        used[0] = budget;
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
        beginFrame();
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
        // A table with its remaining-budget counter below zero has no room left
        return rowCount < MAX_ROWS_PER_FRAME && usedNames[0] >= 0 && usedPrefixes[0] >= 0 && usedDatatypes[0] >= 0;
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
        for (final ColumnState col : columns) {
            col.resetFrameState();
        }
        nameEntries.clear();
        prefixEntries.clear();
        datatypeEntries.clear();
        resetUsedIds();
        rowCount = 0;
    }

    @Override
    public SparqlResultsFrame endFrame() {
        if (columns == null) {
            throw new RdfProtoSerializationError("Variables must be set before ending a frame.");
        }
        beginFrame();
        for (final ColumnState col : columns) {
            if (col.runLength > 0 && col.runNode == null) {
                // Trailing unbound run – omitted, the decoder pads with unbound cells.
                col.runLength = 0;
            } else {
                finalizeRun(col);
            }
        }

        final SparqlResultsFrame.Mutable frame = SparqlResultsFrame.newInstance();
        if (firstFrame) {
            frame.setOptions(options);
        }

        // Effective column types for this frame vs the last emitted header: any difference
        // means the header has to be restated.
        boolean typesChanged = false;
        for (final ColumnState col : columns) {
            if (effectiveType(col) != col.lastEmittedType) {
                typesChanged = true;
                break;
            }
        }
        if (firstFrame || typesChanged) {
            // Emit (or restate) the header
            final int[] columnIndices = new int[columns.length];
            int nextIndex = 0;
            for (byte type = TYPE_IRI; type <= TYPE_POLY; type++) {
                for (int i = 0; i < columns.length; i++) {
                    if (effectiveType(columns[i]) == type) {
                        columnIndices[i] = nextIndex++;
                    }
                }
            }
            for (int i = 0; i < variableNames.length; i++) {
                frame.addVariables(
                    SparqlVariable.newInstance().setName(variableNames[i]).setColumnIndex(columnIndices[i])
                );
                columns[i].lastEmittedType = effectiveType(columns[i]);
            }
        }

        frame.setRowCount(rowCount);
        frame.setNames(nameEntries);
        frame.setPrefixes(prefixEntries);
        frame.setDatatypes(datatypeEntries);

        // Emit the columns grouped by type, in variable order within each group – the same
        // order in which the column indices were assigned.
        for (int i = 0; i < columns.length; i++) {
            final ColumnState col = columns[i];
            if (effectiveType(col) == TYPE_IRI) {
                final SparqlIriColumn.Mutable column = SparqlIriColumn.newInstance();
                column.setNameIds(col.nameIds);
                // For an IRI column the aux ids are its uncompressed prefix ids.
                // If the whole column stays on one prefix, it is stated
                // once instead of once per value.
                final RepeatedInt prefixes = col.auxIds;
                final int valueCount = prefixes.size();
                final int columnPrefix = valueCount == 0 ? 0 : prefixes.get(0);
                boolean onePrefix = true;
                for (int j = 1; j < valueCount; j++) {
                    if (prefixes.get(j) != columnPrefix) {
                        onePrefix = false;
                        break;
                    }
                }
                if (!onePrefix) {
                    // Rewrite the buffer into the "same prefix as the previous IRI" inference
                    // of RdfIri in place.
                    final int[] raw = prefixes.array();
                    int prev = -1;
                    for (int j = 0; j < valueCount; j++) {
                        final int prefix = raw[j];
                        raw[j] = prefix == prev ? 0 : prefix;
                        prev = prefix;
                    }
                    column.setPrefixIds(prefixes);
                } else if (columnPrefix != 0) {
                    prefixes.clear();
                    prefixes.add(columnPrefix);
                    column.setPrefixIds(prefixes);
                }
                // Otherwise every value has prefix id 0, which the decoder assumes anyway
                column.setLayouts(col.layout);
                frame.addIriColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            final ColumnState col = columns[i];
            if (effectiveType(col) == TYPE_BNODE) {
                final SparqlBnodeColumn.Mutable column = SparqlBnodeColumn.newInstance();
                column.setValues(col.strings);
                column.setLayouts(col.layout);
                frame.addBnodeColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            final ColumnState col = columns[i];
            if (effectiveType(col) == TYPE_LITERAL) {
                final SparqlLiteralColumn.Mutable column = SparqlLiteralColumn.newInstance();
                // If every value has the same datatype  the column states it
                // once and contains only the lexical forms, already sitting in the buffer.
                // An empty column counts as simple literals.
                final int datatype = col.columnDatatype == DATATYPE_NONE ? 0 : col.columnDatatype;
                if (datatype == MIXED_DATATYPES) {
                    // For a literal column the aux ids are exactly its datatype ids
                    final LiteralBuffer literals = col.poly().literals;
                    final int litCount = col.auxIds.size();
                    int stringIndex = 0;
                    for (int j = 0; j < litCount; j++) {
                        final RdfLiteral.Mutable literal = literals
                            .appendMessage()
                            .setLex(col.strings.get(stringIndex++));
                        final int dt = col.auxIds.get(j);
                        if (dt == LANG_LITERAL) {
                            literal.setLangtag(col.strings.get(stringIndex++));
                        } else if (dt != 0) {
                            literal.setDatatype(dt);
                        }
                    }
                    column.setValues(literals);
                } else {
                    column.setLexValues(col.strings);
                    if (datatype != 0) {
                        column.setDatatype(datatype);
                    }
                    // Datatype 0 means simple literals, which the decoder assumes anyway
                }
                column.setLayouts(col.layout);
                frame.addLiteralColumns(column);
            }
        }
        for (int i = 0; i < columns.length; i++) {
            final ColumnState col = columns[i];
            // Slow path: polymorphic column
            if (effectiveType(col) == TYPE_POLY) {
                final SparqlPolyColumn.Mutable column = SparqlPolyColumn.newInstance();
                final PolyBuffers poly = col.poly();
                final TermBuffer terms = poly.terms;
                // Iterates through the per-type buffers: the tags say which buffer the next value
                // sits in.
                int iriIndex = 0;
                int auxIndex = 0;
                int stringIndex = 0;
                // -1 forces the first IRI of the column to carry its prefix id
                int prevPrefix = -1;
                for (int v = 0; v < col.valueCount; v++) {
                    final SparqlTerm.Mutable term = terms.appendMessage();
                    switch (col.tags[v]) {
                        case TYPE_IRI -> {
                            final int nameId = col.nameIds.get(iriIndex++);
                            final int rawPrefix = col.auxIds.get(auxIndex++);
                            final int prefixId = rawPrefix == prevPrefix ? 0 : rawPrefix;
                            prevPrefix = rawPrefix;
                            if (prefixId == 0 && nameId == 0) {
                                term.setIri(ZERO_IRI);
                            } else {
                                term.setIri(poly.iris.append().setPrefixId(prefixId).setNameId(nameId));
                            }
                        }
                        case TYPE_BNODE -> term.setBnode(col.strings.get(stringIndex++));
                        default -> {
                            final RdfLiteral.Mutable literal = poly.literals
                                .appendMessage()
                                .setLex(col.strings.get(stringIndex++));
                            final int dt = col.auxIds.get(auxIndex++);
                            if (dt == LANG_LITERAL) {
                                literal.setLangtag(col.strings.get(stringIndex++));
                            } else if (dt != 0) {
                                literal.setDatatype(dt);
                            }
                            term.setLiteral(literal);
                        }
                    }
                }
                column.setValues(terms);
                column.setLayouts(col.layout);
                frame.addPolyColumns(column);
            }
        }

        firstFrame = false;
        // The frame points at the encoder's buffers, so they stay untouched until the next frame
        framePending = true;

        // Pre-calculate the serialized size, while all objects are likely still in cache.
        frame.getSerializedSize();
        return frame;
    }

    private void addCell(ColumnState col, TNode node) {
        if (node == null) {
            if (col.runLength > 0 && col.runNode == null) {
                col.runLength++;
                return;
            }
            finalizeRun(col);
            col.runLength = 1;
            return;
        }
        // runNode != null means an active bound run
        if (node.equals(col.runNode)) {
            col.runLength++;
            return;
        }
        finalizeRun(col);
        col.runLength = 1;
        col.runNode = node;
        encodeValue(col, node);
    }

    private void encodeValue(ColumnState col, TNode node) {
        currentColumn = col;
        final Object encoded = converter.nodeToProto(this, node);
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
            // Lazy-switch to a polymorphic column
            col.backfillTags(col.type);
            col.type = TYPE_POLY;
        }
        col.addValueTag(valueType);
    }

    // Never-bound columns are emitted as IRI columns
    private static byte effectiveType(ColumnState col) {
        return col.type == TYPE_UNSET ? TYPE_IRI : col.type;
    }

    private void finalizeRun(ColumnState col) {
        if (col.runLength == 0) {
            return;
        }
        if (col.runNode == null) {
            emitException(col, KIND_UNBOUND, col.runLength - 1);
        } else if (col.runLength >= 2) {
            emitException(col, KIND_REPEAT, col.runLength - 2);
        } else {
            col.skip++;
        }
        col.runLength = 0;
        col.runNode = null;
    }

    private void emitException(ColumnState col, int kind, int len) {
        final int lenCode = Math.min(len, MAX_INLINE_LEN);
        col.layout.add((col.skip << 5) | (kind << 4) | lenCode);
        if (lenCode == MAX_INLINE_LEN) {
            col.layout.add(len - MAX_INLINE_LEN);
        }
        col.skip = 0;
    }

    @Override
    public void appendNameEntry(RdfNameEntry nameEntry) {
        nameEntries.append(usedNames, "name", nameEntry.getId(), nameEntry.getValue());
    }

    @Override
    public void appendPrefixEntry(RdfPrefixEntry prefixEntry) {
        prefixEntries.append(usedPrefixes, "prefix", prefixEntry.getId(), prefixEntry.getValue());
    }

    @Override
    public void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry) {
        datatypeEntries.append(usedDatatypes, "datatype", datatypeEntry.getId(), datatypeEntry.getValue());
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
    private static void checkAndMarkUsed(long[] used, int id, String kind) {
        if (isUsed(used, id)) {
            throw new RdfProtoSerializationError(
                (
                    "The %s lookup table is too small to encode a single row of these results: " +
                    "entry %d would be overwritten while still referenced in the current frame. " +
                    "Increase the max %s table size."
                ).formatted(kind, id, kind)
            );
        }
        markUsed(used, id);
    }
}
