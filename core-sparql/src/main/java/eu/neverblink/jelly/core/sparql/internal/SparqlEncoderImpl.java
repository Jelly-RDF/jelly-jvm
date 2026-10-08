package eu.neverblink.jelly.core.sparql.internal;

import com.google.protobuf.ByteString;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfColumn;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfDefaultGraph;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;
import eu.neverblink.jelly.core.proto.v1.sparql.*;
import eu.neverblink.jelly.core.sparql.JellySparqlConstants;
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
 * Builds the columnar frames, one column per variable: runs of equal consecutive values and
 * unbound cells are compressed into the layout field, and every value is written straight into
 * the list of its term type in the column. A column whose values in a frame are of more than one
 * type also gets the kinds field.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@InternalApi
public final class SparqlEncoderImpl<TNode> extends SparqlEncoder<TNode> implements NodeEncoder<TNode> {

    // Term types in the kinds field of a column
    private static final int TERM_IRI = 0;
    private static final int TERM_LITERAL = 1;
    private static final int TERM_BNODE = 2;
    private static final int TERM_TRIPLE = 3;

    private static final int KIND_REPEAT = 0;
    private static final int KIND_UNBOUND = 1;

    // Frames are ended after this many rows, so that what we write stays readable
    // by a reader running with the default row limit.
    private static final int ROW_LIMIT_PER_FRAME = JellySparqlConstants.DEFAULT_MAX_ROWS_PER_FRAME;

    // Run lengths of 0–14 are inlined in the token. 15 uses an extension varint.
    private static final int MAX_INLINE_LEN = 15;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    private static final RdfIri ZERO_IRI = RdfIri.newInstance();

    // Used as a type marker – the literal's parts go into the column buffers,
    // not into the returned proto message.
    private static final RdfLiteral LITERAL_MARKER = RdfLiteral.newInstance();
    // Same for triple terms – the term itself goes into ColumnExtras.tripleTerms
    private static final RdfTriple TRIPLE_MARKER = RdfTriple.newInstance();

    private static final int MAX_TRIPLE_TERM_DEPTH = 32;

    private static final String RDF_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";
    private static final String RDF_DIR_LANG_STRING = "http://www.w3.org/1999/02/22-rdf-syntax-ns#dirLangString";

    /**
     * Column state of the current frame, filled in from beginFrame() through to endFrame(). The
     * buffers are the column's lists in the frame: endFrame() hands them to the column message.
     */
    private static final class ColumnState {

        // Run-length state. A run is active while runLength > 0. runNode == null then means
        // a run of unbound cells. Whenever runLength is 0, runNode is null too.
        Object runNode = null;
        int runLength = 0;
        // runNode's hashCode(), while a bound run is active
        int runHash = 0;
        // Number of values emitted exactly once since the last layout exception
        int skip = 0;

        // Per-frame, per-column IRI name inference state. The prefix side of the inference is
        // resolved at frame end.
        int lastNameId = 0;

        // Number of run values in the frame, and the term types among them (1 << TERM_*)
        int valueCount = 0;
        int termsUsed = 0;

        // Sequence layout
        final RepeatedInt layout = RepeatedInt.newEmptyInstance();
        // IRIs: name ids with the next-name inference already applied (0 means "previous + 1"),
        // and the raw prefix ids, compressed at frame end
        final RepeatedInt nameIds = RepeatedInt.newEmptyInstance();
        final RepeatedInt prefixIds = RepeatedInt.newEmptyInstance();
        // Literals: lexical forms and one kind per literal, compressed at frame end
        final RepeatedString lexValues = RepeatedString.newEmptyInstance();
        final RepeatedInt literalKinds = RepeatedInt.newEmptyInstance();
        // Blank node labels
        final RepeatedString bnodes = RepeatedString.newEmptyInstance();

        // Lazily created – see ColumnExtras
        private ColumnExtras extras = null;

        ColumnExtras extras() {
            if (extras == null) {
                extras = new ColumnExtras();
            }
            return extras;
        }

        /** Records the term type of the next run value. */
        void addTerm(int term) {
            // Almost always the same single type as the values before
            if (termsUsed != 1 << term) {
                addTermSlow(term);
            }
            valueCount++;
        }

        private void addTermSlow(int term) {
            final int used = termsUsed;
            termsUsed = used | (1 << term);
            if (used == 0) {
                // The first value of the frame
                return;
            }
            final ColumnExtras extras = extras();
            if (Integer.bitCount(used) == 1) {
                // The first value of a second type: every value so far had the one type
                extras.fillKinds(Integer.numberOfTrailingZeros(used), valueCount);
            }
            extras.setKind(valueCount, term);
        }

        void resetFrameState() {
            runNode = null;
            runLength = 0;
            skip = 0;
            lastNameId = 0;
            valueCount = 0;
            termsUsed = 0;
            layout.clear();
            nameIds.clear();
            prefixIds.clear();
            lexValues.clear();
            literalKinds.clear();
            bnodes.clear();
            if (extras != null) {
                extras.resetFrameState();
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
        col.prefixIds.add(prefixId);
        col.lastNameId = nameId;
        col.addTerm(TERM_IRI);
    }

    @Override
    public String makeBlankNode(String label) {
        final String bnode = getLookupEncoder().makeBlankNode(label);
        final ColumnState col = currentColumn;
        col.bnodes.add(bnode);
        col.addTerm(TERM_BNODE);
        return bnode;
    }

    @Override
    public RdfLiteral makeSimpleLiteral(String lex) {
        // No lookup table involved – the underlying encoder (and its cache) is skipped.
        // The reasoning here is that literals in SPARQL results repeat rarely anyway,
        // so this saves quite a lot of cache thrashing. Also, the cost of encoding a literal
        // here is much smaller than in RDF (no allocations).
        return appendLiteral(currentColumn, lex, 0);
    }

    @Override
    public RdfLiteral makeLangLiteral(TNode lit, String lex, String lang) {
        final ColumnState col = currentColumn;
        return appendLiteral(col, lex, langKind(col.extras().langtagIndex(lang, RdfBaseDirection.UNSPECIFIED)));
    }

    @Override
    public RdfLiteral makeDirLangLiteral(TNode lit, String lex, String lang, RdfBaseDirection direction) {
        checkBaseDirectionsAllowed();
        final ColumnState col = currentColumn;
        return appendLiteral(col, lex, langKind(col.extras().langtagIndex(lang, direction)));
    }

    @Override
    public RdfLiteral makeDtLiteral(TNode lit, String lex, String dt) {
        // The underlying encoder is still consulted for the datatype lookup id (and the
        // lookup entry emission that comes with it)
        final int datatype = getLookupEncoder().makeDtLiteral(lit, lex, dt).getDatatype();
        markUsed(usedDatatypes, datatype);
        return appendLiteral(currentColumn, lex, datatypeKind(datatype));
    }

    private static RdfLiteral appendLiteral(ColumnState col, String lex, int kind) {
        col.lexValues.add(lex);
        col.literalKinds.add(kind);
        col.addTerm(TERM_LITERAL);
        return LITERAL_MARKER;
    }

    @Override
    public RdfTriple makeQuotedTriple(TNode s, TNode p, TNode o) {
        checkTripleTermsAllowed();
        final ColumnState col = currentColumn;
        final TripleTermEncoder tripleEncoder = new TripleTermEncoder(col);
        final RdfTripleTerm.Mutable term = tripleEncoder.encode(s, p, o, 1);
        final ColumnExtras extras = col.extras();
        extras.tripleTerms.add(term);
        col.addTerm(TERM_TRIPLE);
        final int iris = tripleEncoder.iriCount;
        if (iris > extras.maxTripleTermIris) {
            // Every later row of this frame may need that many IRI lookup entries for this column
            // too, not just one – reserve the difference now. Later frames reserve it from the
            // start (see resetUsedIds).
            final int extra = iris - Math.max(1, extras.maxTripleTermIris);
            extras.maxTripleTermIris = iris;
            reserveUsedIds(usedNames, options.getMaxNameTableSize(), extra);
            reserveUsedIds(usedPrefixes, options.getMaxPrefixTableSize(), extra);
        }
        return TRIPLE_MARKER;
    }

    private static void reserveUsedIds(long[] used, int tableSize, int count) {
        if (tableSize != 0 && count > 0) {
            used[0] -= count;
        }
    }

    private void checkBaseDirectionsAllowed() {
        if (options.getRdfVersion() == RdfVersion.RDF_VERSION_1_1) {
            throw new RdfProtoSerializationError(
                "The stream declares RDF 1.1, which does not allow literals with a base direction."
            );
        }
    }

    private void checkTripleTermsAllowed() {
        final RdfVersion version = options.getRdfVersion();
        if (version == RdfVersion.RDF_VERSION_1_1 || version == RdfVersion.RDF_VERSION_1_2_BASIC) {
            throw new RdfProtoSerializationError(
                "The stream declares %s, which does not allow triple terms.".formatted(
                    version == RdfVersion.RDF_VERSION_1_1 ? "RDF 1.1" : "RDF 1.2 Basic"
                )
            );
        }
    }

    /**
     * Encodes one triple term, with its nested triple terms, into an RdfTripleTerm message.
     * <p>
     * The IRIs of the term take part in the IRI inference of the column's triple terms, which is
     * separate from that of its IRI values, in the order subject, predicate, object – which is
     * also the order in which they are encoded here.
     * Created for each triple term, which is fine, as they are rare and this will pretty much
     * always fit in TLAB.
     */
    private final class TripleTermEncoder implements NodeEncoder<TNode> {

        private final ColumnState col;
        // Nesting depth of the term being encoded, 1 for the top level
        private int depth;
        // The message of the term encoded last
        private Object last;
        // Number of IRIs in the whole triple term
        int iriCount = 0;

        TripleTermEncoder(ColumnState col) {
            this.col = col;
        }

        RdfTripleTerm.Mutable encode(TNode s, TNode p, TNode o, int depth) {
            if (depth > MAX_TRIPLE_TERM_DEPTH) {
                throw new RdfProtoSerializationError(
                    "Triple terms nested deeper than %d levels are not supported.".formatted(MAX_TRIPLE_TERM_DEPTH)
                );
            }
            final RdfTripleTerm.Mutable term = RdfTripleTerm.newInstance();
            final Object subject = encodeTerm(s, depth);
            if (subject instanceof RdfIri iri) {
                term.setSIri(iri);
            } else if (subject instanceof String bnode) {
                term.setSBnode(bnode);
            } else {
                throw new RdfProtoSerializationError(
                    "The subject of a triple term must be an IRI or a blank node, got: %s".formatted(s)
                );
            }
            if (!(encodeTerm(p, depth) instanceof RdfIri predicate)) {
                throw new RdfProtoSerializationError(
                    "The predicate of a triple term must be an IRI, got: %s".formatted(p)
                );
            }
            term.setPIri(predicate);
            final Object object = encodeTerm(o, depth);
            if (object instanceof RdfIri iri) {
                term.setOIri(iri);
            } else if (object instanceof String bnode) {
                term.setOBnode(bnode);
            } else if (object instanceof RdfLiteral literal) {
                term.setOLiteral(literal);
            } else if (object instanceof RdfTripleTerm triple) {
                term.setOTripleTerm(triple);
            } else {
                throw new RdfProtoSerializationError("Unsupported object of a triple term: %s".formatted(o));
            }
            return term;
        }

        private Object encodeTerm(TNode node, int depth) {
            this.depth = depth;
            last = null;
            converter.nodeToProto(this, node);
            return last;
        }

        private RdfIri iri(String iri, boolean inference) {
            final long ids = getLookupEncoder().makeIriIds(iri);
            final int nameId = (int) (ids >>> 32);
            final int prefixId = (int) ids;
            markUsed(usedNames, nameId);
            if (prefixId != 0) {
                markUsed(usedPrefixes, prefixId);
            }
            // The IRIs of the column's triple terms have an inference state of their own
            final ColumnExtras extras = col.extras();
            final int storedNameId = inference && nameId == extras.tripleLastNameId + 1 ? 0 : nameId;
            final int storedPrefixId = prefixId == extras.tripleLastPrefixId ? 0 : prefixId;
            extras.tripleLastNameId = nameId;
            extras.tripleLastPrefixId = prefixId;
            iriCount++;
            final RdfIri.Mutable message = RdfIri.newInstance().setPrefixId(storedPrefixId).setNameId(storedNameId);
            last = message;
            return message;
        }

        @Override
        public RdfIri makeIri(String iri) {
            return iri(iri, true);
        }

        @Override
        public RdfIri makeIriRaw(String iri) {
            return iri(iri, false);
        }

        @Override
        public String makeBlankNode(String label) {
            final String bnode = getLookupEncoder().makeBlankNode(label);
            last = bnode;
            return bnode;
        }

        @Override
        public RdfLiteral makeSimpleLiteral(String lex) {
            last = RdfLiteral.newInstance().setLex(lex);
            return LITERAL_MARKER;
        }

        @Override
        public RdfLiteral makeLangLiteral(TNode lit, String lex, String lang) {
            last = RdfLiteral.newInstance().setLex(lex).setLangtag(lang);
            return LITERAL_MARKER;
        }

        @Override
        public RdfLiteral makeDirLangLiteral(TNode lit, String lex, String lang, RdfBaseDirection direction) {
            checkBaseDirectionsAllowed();
            last = RdfLiteral.newInstance().setLex(lex).setLangtag(lang).setDirection(direction);
            return LITERAL_MARKER;
        }

        @Override
        public RdfLiteral makeDtLiteral(TNode lit, String lex, String dt) {
            final int datatype = getLookupEncoder().makeDtLiteral(lit, lex, dt).getDatatype();
            markUsed(usedDatatypes, datatype);
            last = RdfLiteral.newInstance().setLex(lex).setDatatype(datatype);
            return LITERAL_MARKER;
        }

        @Override
        public RdfTriple makeQuotedTriple(TNode s, TNode p, TNode o) {
            final int outer = depth;
            final RdfTripleTerm.Mutable nested = encode(s, p, o, outer + 1);
            depth = outer;
            last = nested;
            return TRIPLE_MARKER;
        }

        @Override
        public RdfDefaultGraph makeDefaultGraph() {
            throw new RdfProtoSerializationError("The default graph cannot occur in a triple term.");
        }
    }

    @Override
    public RdfDefaultGraph makeDefaultGraph() {
        throw new RdfProtoSerializationError("The default graph is not a valid SPARQL result binding.");
    }

    private String[] variableNames = null;
    private ColumnState[] columns = null;
    private int rowCount = 0;
    // Whether the next frame is the first frame of the stream, which contains the stream options
    private boolean firstFrame = true;
    // Whether the next frame is the first frame of a result set, which contains the header
    private boolean resultSetStart = true;

    private ColumnState currentColumn = null;

    // True while the buffers still hold the contents of the frame endFrame last returned.
    private boolean framePending = false;

    // Special flags to be used on the rowCount field.
    // They are only relevant where rowCount is not used – this way we don't need additional
    // fields in this class, which would take up another cache line.
    private static final int ROW_COUNT_ROW_FAILED = -1;
    private static final int ROW_COUNT_STREAM_ENDED = -2;

    /**
     * The lookup ids used by the current frame – both the ids assigned by
     * its lookup entries and the ids its columns refer to. One bit per id, starting at
     * slot 1. Slot 0 is the remaining-budget counter: it starts at the table's budget,
     * every fresh mark decrements it, and a negative value means the frame has no room
     * for another row.
     * <p>
     * A frame's lookup entries are all applied before any of its columns, so an entry that
     * the frame overwrites while still referring to the old value cannot be represented. Each
     * frame is an epoch of the lookups (see resetUsedIds), which never evict an entry used in the
     * current epoch, so the frame stays safe exactly as long as it has not touched every id of
     * the table. The budget is set so that one more row of fresh ids still fits: the table size
     * minus one potential id per variable, floored at zero.
     * <p>
     * A triple term can need an IRI id for each of its IRIs, so a column that held triple terms
     * reserves as many IRI ids per row as its largest triple term had (see resetUsedIds and
     * makeQuotedTriple). This cannot help the first triple term of a column, if it comes in the
     * last few rows of an almost full frame: such a row can still fail with the "too small to
     * encode a single row" error.
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
        if (tableSize == 0) {
            return Long.MAX_VALUE;
        }
        // Floor at 0 to avoid a situation where there are more variables than slots
        // in e.g., datatype table, which causes the budget to be exhausted on the
        // first row.
        return Math.max(tableSize - variables, 0);
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
        if (options.getRdfVersion() == null) {
            throw new RdfProtoSerializationError("Unknown RDF version: %d".formatted(options.getRdfVersionValue()));
        }
        if (options.getStreamType() == null) {
            throw new RdfProtoSerializationError("Unknown stream type: %d".formatted(options.getStreamTypeValue()));
        }
        usedNames = newUsedIds(options.getMaxNameTableSize());
        usedPrefixes = newUsedIds(options.getMaxPrefixTableSize());
        usedDatatypes = newUsedIds(options.getMaxDatatypeTableSize());
    }

    @Override
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
        columns = new ColumnState[variableNames.length];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = new ColumnState();
        }
        resetUsedIds();
    }

    /** Clears the used-ids bits and puts each remaining-budget counter back at its budget. */
    private void resetUsedIds() {
        getLookupEncoder().newEpoch();
        final int n = columns.length;
        int iris = n;
        for (final ColumnState col : columns) {
            if (col.extras != null && col.extras.maxTripleTermIris > 1) {
                iris += col.extras.maxTripleTermIris - 1;
            }
        }
        resetUsedIds(usedNames, usedIdsBudget(options.getMaxNameTableSize(), iris));
        resetUsedIds(usedPrefixes, usedIdsBudget(options.getMaxPrefixTableSize(), iris));
        resetUsedIds(usedDatatypes, usedIdsBudget(options.getMaxDatatypeTableSize(), n));
    }

    private static void resetUsedIds(long[] used, long budget) {
        Arrays.fill(used, 0L);
        used[0] = budget;
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
                addCell(columns[i], row[i]);
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
        return rowCount < ROW_LIMIT_PER_FRAME && usedNames[0] >= 0 && usedPrefixes[0] >= 0 && usedDatatypes[0] >= 0;
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

        if (resultSetStart) {
            for (final String name : variableNames) {
                frame.addVariables(name);
            }
        }

        if (trailer != null) {
            frame.setTrailer(trailer);
        }
        frame.setRowCount(rowCount);
        frame.setNames(nameEntries);
        frame.setPrefixes(prefixEntries);
        frame.setDatatypes(datatypeEntries);
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

    /**
     * Emits one column per variable, in variable order. The column messages point at the column
     * buffers, which are only compressed in place here.
     */
    private void addColumns(SparqlResultsFrame.Mutable frame) {
        for (final ColumnState col : columns) {
            final RdfColumn.Mutable column = RdfColumn.newInstance()
                .setLayouts(col.layout)
                .setNameIds(col.nameIds)
                .setLexValues(col.lexValues)
                .setBnodes(col.bnodes);
            compressPrefixIds(col.prefixIds);
            column.setPrefixIds(col.prefixIds);
            compressLiteralKinds(col.literalKinds);
            column.setLiteralKinds(col.literalKinds);
            final ColumnExtras extras = col.extras;
            if (extras != null) {
                column.setLangtags(extras.langtags);
                if (extras.hasDirections()) {
                    column.setLangtagDirections(extras.langtagDirections);
                }
                for (final RdfTripleTerm term : extras.tripleTerms) {
                    column.addTripleTerms(term);
                }
            }
            // Only needed if the values are of more than one type
            if (Integer.bitCount(col.termsUsed) > 1) {
                column.setKinds(ByteString.copyFrom(extras.kinds, 0, (col.valueCount + 3) >> 2));
            }
            frame.addColumns(column);
        }
    }

    /**
     * Compresses the raw prefix ids of a column, in place. If every IRI has the same prefix, it is
     * stated once (or not at all, if it is 0). Otherwise, the "same prefix as the previous IRI"
     * inference of RdfIri applies along the list.
     */
    private static void compressPrefixIds(RepeatedInt prefixes) {
        final int valueCount = prefixes.size();
        if (valueCount == 0) {
            return;
        }
        final int[] raw = prefixes.array();
        final int first = raw[0];
        int j = 1;
        while (j < valueCount && raw[j] == first) {
            j++;
        }
        if (j == valueCount) {
            prefixes.clear();
            if (first != 0) {
                prefixes.add(first);
            }
            return;
        }
        // The first IRI states its prefix, every later one only if it differs from the one before
        int prev = first;
        for (int k = 1; k < valueCount; k++) {
            final int prefix = raw[k];
            raw[k] = prefix == prev ? 0 : prefix;
            prev = prefix;
        }
    }

    /**
     * Compresses the literal kinds of a column, in place: no list if every literal is simple, one
     * entry if every literal has the same kind.
     */
    private static void compressLiteralKinds(RepeatedInt kinds) {
        final int count = kinds.size();
        if (count == 0) {
            return;
        }
        final int first = kinds.get(0);
        for (int i = 1; i < count; i++) {
            if (kinds.get(i) != first) {
                return;
            }
        }
        kinds.clear();
        if (first != 0) {
            kinds.add(first);
        }
    }

    /** The literal kind of a literal with the given datatype lookup id. */
    private static int datatypeKind(int datatype) {
        return 2 * datatype - 1;
    }

    /** The literal kind of a language-tagged string with the given index in langtags. */
    private static int langKind(int langtagIndex) {
        return 2 * langtagIndex + 2;
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
        // runNode != null means an active bound run. Most cells differ from the one before, and
        // their hash codes (cached by the nodes or their strings) tell them apart without
        // comparing two strings of the same length byte by byte.
        final int hash = node.hashCode();
        final Object runNode = col.runNode;
        if (runNode != null && hash == col.runHash && node.equals(runNode)) {
            col.runLength++;
            return;
        }
        finalizeRun(col);
        col.runLength = 1;
        col.runNode = node;
        col.runHash = hash;
        encodeValue(col, node);
    }

    private void encodeValue(ColumnState col, TNode node) {
        currentColumn = col;
        final int valuesBefore = col.valueCount;
        converter.nodeToProto(this, node);
        if (col.valueCount == valuesBefore) {
            throw new RdfProtoSerializationError("Unsupported term type in SPARQL results: %s".formatted(node));
        }
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
        final String value = datatypeEntry.getValue();
        if (RDF_LANG_STRING.equals(value) || RDF_DIR_LANG_STRING.equals(value)) {
            throw new RdfProtoSerializationError(
                "A literal with the datatype %s must have a language tag.".formatted(value)
            );
        }
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
