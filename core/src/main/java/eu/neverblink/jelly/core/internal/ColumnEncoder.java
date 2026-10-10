package eu.neverblink.jelly.core.internal;

import static eu.neverblink.jelly.core.internal.ColumnLayout.*;

import com.google.protobuf.ByteString;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfColumn;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Encoder of RdfColumn messages, shared by Jelly-RDF and Jelly-SPARQL.
 * <p>
 * The owner of this encoder controls the framing: it calls {@link #resetFrame}
 * before the first cell of a frame, {@link #hasRoom()} before every row after the first, and
 * {@link #endRuns(Column[])} and {@link #buildColumn(Column)} at the end. The lookup entries the
 * frame needs are collected in {@link #nameEntries()}, {@link #prefixEntries()} and
 * {@link #datatypeEntries()}.
 *
 * @param <TNode> the type of RDF nodes in the library
 */
@InternalApi
public final class ColumnEncoder<TNode> implements RdfBufferAppender {

    /** Masks of term types allowed in a column, for {@link #newColumn}. */
    public static final int TERMS_ALL = 0b1111;
    public static final int TERMS_IRI = 1 << TERM_IRI;
    public static final int TERMS_RESOURCE = (1 << TERM_IRI) | (1 << TERM_BNODE);

    private static final int MAX_TRIPLE_TERM_DEPTH = 32;

    /**
     * Column state of the current frame, filled in from resetFrame() through to buildColumn().
     *
     * @param <TNode> the type of RDF nodes in the library
     */
    public static final class Column<TNode> implements NodeEncoder<TNode> {

        private final ColumnEncoder<TNode> encoder;

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

        // Set when the converter describes the graph name of a new run as the default graph
        boolean defaultGraphSeen = false;

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

        // The term types this column may hold (TERMS_*), and its name in error messages
        private final int allowedTerms;
        private final String position;
        // What the default graph means in this column: null means an unbound cell, otherwise
        // this is the message of the error it raises.
        private final String defaultGraphError;

        private Column(ColumnEncoder<TNode> encoder, int allowedTerms, String position, String defaultGraphError) {
            this.encoder = encoder;
            this.allowedTerms = allowedTerms;
            this.position = position;
            this.defaultGraphError = defaultGraphError;
        }

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
            if ((allowedTerms & (1 << term)) == 0) {
                throw notAllowedTerm(term);
            }
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

        private RdfProtoSerializationError notAllowedTerm(int term) {
            final String what = switch (term) {
                case TERM_IRI -> "an IRI";
                case TERM_LITERAL -> "a literal";
                case TERM_BNODE -> "a blank node";
                default -> "a triple term";
            };
            return new RdfProtoSerializationError(
                (
                    "The %s of a statement cannot be %s. Generalized statements, and triple terms " +
                    "anywhere but in the object (as in RDF-star), can only be written in " +
                    "Jelly-RDF 1.1 (stream options version 2)."
                ).formatted(position, what)
            );
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

        /** Whether the column has any value in the current frame, or any unbound cell. */
        public boolean isEmpty() {
            return valueCount == 0 && layout.isEmpty() && runLength == 0;
        }

        /** Whether every cell of the column in the current frame is unbound. */
        public boolean isAllUnbound() {
            return valueCount == 0;
        }

        // *** NodeEncoder – the converter describes the value of a new run ***

        @Override
        public void iri(String iri) {
            final long ids = encoder.lookupEncoder.makeIriIds(iri);
            final int nameId = (int) (ids >>> 32);
            final int prefixId = (int) ids;
            encoder.markIriUsed(nameId, prefixId);
            nameIds.add(nameId == lastNameId + 1 ? 0 : nameId);
            prefixIds.add(prefixId);
            lastNameId = nameId;
            addTerm(TERM_IRI);
        }

        @Override
        public void blankNode(String label) {
            bnodes.add(label);
            addTerm(TERM_BNODE);
        }

        @Override
        public void simpleLiteral(String lex) {
            // No lookup table involved, and no cache: literals rarely repeat in a column apart
            // from runs, which the layout handles anyway.
            addLiteral(lex, 0);
        }

        @Override
        public void langLiteral(String lex, String lang) {
            addLiteral(lex, langKind(extras().langtagIndex(lang, RdfBaseDirection.UNSPECIFIED)));
        }

        @Override
        public void dirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
            encoder.checkBaseDirectionsAllowed();
            addLiteral(lex, langKind(extras().langtagIndex(lang, direction)));
        }

        @Override
        public void dtLiteral(String lex, String datatype) {
            addLiteral(lex, datatypeKind(encoder.makeDatatypeId(datatype)));
        }

        private void addLiteral(String lex, int kind) {
            lexValues.add(lex);
            literalKinds.add(kind);
            addTerm(TERM_LITERAL);
        }

        @Override
        public void tripleTerm(TNode s, TNode p, TNode o) {
            encoder.addTripleTerm(this, s, p, o);
        }

        @Override
        public void defaultGraph() {
            if (defaultGraphError != null) {
                throw new RdfProtoSerializationError(defaultGraphError);
            }
            defaultGraphSeen = true;
        }
    }

    private final ProtoEncoderConverter<TNode> converter;
    private final LookupEncoder lookupEncoder;
    private final RdfVersion rdfVersion;
    private final int maxNameTableSize;
    private final int maxPrefixTableSize;
    private final int maxDatatypeTableSize;

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
     * minus one potential id per column, floored at zero.
     * <p>
     * A triple term can need an IRI id for each of its IRIs, so a column that held triple terms
     * reserves as many IRI ids per row as its largest triple term had (see resetUsedIds and
     * addTripleTerm). This cannot help the first triple term of a column, if it comes in the
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

    // IRI inference state of the namespace declarations of the current frame
    private int namespaceLastNameId = 0;
    private int namespaceLastPrefixId = 0;
    private final NamespaceEncoder namespaceEncoder = new NamespaceEncoder();

    /**
     * @param converter the converter of the RDF library
     * @param maxNameTableSize the size of the name lookup table
     * @param maxPrefixTableSize the size of the prefix lookup table, 0 if disabled
     * @param maxDatatypeTableSize the size of the datatype lookup table
     * @param rdfVersion the RDF version declared by the stream, which limits the terms it may have
     */
    public ColumnEncoder(
        ProtoEncoderConverter<TNode> converter,
        int maxNameTableSize,
        int maxPrefixTableSize,
        int maxDatatypeTableSize,
        RdfVersion rdfVersion
    ) {
        this.converter = converter;
        this.rdfVersion = rdfVersion;
        this.maxNameTableSize = maxNameTableSize;
        this.maxPrefixTableSize = maxPrefixTableSize;
        this.maxDatatypeTableSize = maxDatatypeTableSize;
        // Safe to pass `this` here: the lookup encoder only stores it as the receiver of the
        // lookup entries it emits later, during encoding.
        this.lookupEncoder = LookupEncoder.create(this, maxPrefixTableSize, maxNameTableSize, maxDatatypeTableSize);
        usedNames = newUsedIds(maxNameTableSize);
        usedPrefixes = newUsedIds(maxPrefixTableSize);
        usedDatatypes = newUsedIds(maxDatatypeTableSize);
    }

    /**
     * Creates a new column.
     *
     * @param allowedTerms the term types the column may hold (TERMS_* or a mask of 1 &lt;&lt; TERM_*)
     * @param position the name of the column in error messages, e.g., "subject"
     * @param defaultGraphError null if the default graph is an unbound cell in this column,
     *                          otherwise the message of the error it raises
     */
    public Column<TNode> newColumn(int allowedTerms, String position, String defaultGraphError) {
        return new Column<>(this, allowedTerms, position, defaultGraphError);
    }

    // *** Frame management ***

    /**
     * Discards the state of the previous frame and starts a new one: the columns are emptied,
     * the lookup entries cleared, and the lookup budget reset.
     *
     * @param columns all columns of the frame
     * @param datatypeColumns how many of the columns can hold literals
     */
    public void resetFrame(Column<TNode>[] columns, int datatypeColumns) {
        for (final Column<TNode> col : columns) {
            col.resetFrameState();
        }
        nameEntries.clear();
        prefixEntries.clear();
        datatypeEntries.clear();
        namespaceLastNameId = 0;
        namespaceLastPrefixId = 0;
        resetUsedIds(columns, datatypeColumns);
    }

    /**
     * Starts a new lookup epoch: clears the used-ids bits and puts each remaining-budget counter
     * back at its budget. Unlike {@link #resetFrame}, this leaves the columns and the lookup
     * entries of the previous frame alone.
     *
     * @param columns all columns of the next frame
     * @param datatypeColumns how many of the columns can hold literals
     */
    public void resetUsedIds(Column<TNode>[] columns, int datatypeColumns) {
        lookupEncoder.newEpoch();
        int iris = columns.length;
        for (final Column<TNode> col : columns) {
            if (col.extras != null && col.extras.maxTripleTermIris > 1) {
                iris += col.extras.maxTripleTermIris - 1;
            }
        }
        resetUsedIds(usedNames, usedIdsBudget(maxNameTableSize, iris));
        resetUsedIds(usedPrefixes, usedIdsBudget(maxPrefixTableSize, iris));
        resetUsedIds(usedDatatypes, usedIdsBudget(maxDatatypeTableSize, datatypeColumns));
    }

    private static void resetUsedIds(long[] used, long budget) {
        Arrays.fill(used, 0L);
        used[0] = budget;
    }

    /**
     * Whether one more row can be encoded without overflowing the lookup tables in this frame.
     * Must be checked before the row is touched, so that a full frame can be ended and the row
     * retried.
     */
    public boolean hasRoom() {
        return usedNames[0] >= 0 && usedPrefixes[0] >= 0 && usedDatatypes[0] >= 0;
    }

    /**
     * Ends the active runs of the columns, before the frame is built. A trailing unbound run is
     * omitted: the decoder pads the column with unbound cells.
     */
    public void endRuns(Column<TNode>[] columns) {
        for (final Column<TNode> col : columns) {
            if (col.runLength > 0 && col.runNode == null) {
                col.runLength = 0;
            } else {
                finalizeRun(col);
            }
        }
    }

    /**
     * Builds the column message of a column. It points at the column buffers, which are only
     * compressed in place here. Call {@link #endRuns(Column[])} first.
     */
    public RdfColumn.Mutable buildColumn(Column<TNode> col) {
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
        return column;
    }

    /** Name lookup entries of the current frame. */
    public MessageCollection<RdfLookupEntryPacked, RdfLookupEntryPacked.Mutable> nameEntries() {
        return nameEntries;
    }

    /** Prefix lookup entries of the current frame. */
    public MessageCollection<RdfLookupEntryPacked, RdfLookupEntryPacked.Mutable> prefixEntries() {
        return prefixEntries;
    }

    /** Datatype lookup entries of the current frame. */
    public MessageCollection<RdfLookupEntryPacked, RdfLookupEntryPacked.Mutable> datatypeEntries() {
        return datatypeEntries;
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

    // *** Cells ***

    /**
     * Adds a cell to the column.
     *
     * @param col the column
     * @param node the term, or null for an unbound cell
     */
    public void addCell(Column<TNode> col, TNode node) {
        if (node == null) {
            addUnbound(col);
            return;
        }
        // runNode != null means an active bound run. Most cells differ from the one before, and
        // their hash codes (cached by the nodes or their strings) tell them apart without
        // comparing two strings byte by byte.
        final int hash = node.hashCode();
        final Object runNode = col.runNode;
        if (runNode != null && hash == col.runHash && node.equals(runNode)) {
            col.runLength++;
            return;
        }
        startRun(col, node, hash);
        converter.encodeAny(col, node);
    }

    /**
     * Adds a cell to the subject column of RDF statements.
     *
     * @param col the column
     * @param node the subject
     */
    public void addSubjectCell(Column<TNode> col, TNode node) {
        if (node == null) {
            throw missingTerm(col);
        }
        final int hash = node.hashCode();
        final Object runNode = col.runNode;
        if (runNode != null && hash == col.runHash && node.equals(runNode)) {
            col.runLength++;
            return;
        }
        startRun(col, node, hash);
        converter.encodeResource(col, node);
    }

    /**
     * Adds a cell to the predicate column of RDF statements.
     *
     * @param col the column
     * @param node the predicate
     */
    public void addPredicateCell(Column<TNode> col, TNode node) {
        if (node == null) {
            throw missingTerm(col);
        }
        final int hash = node.hashCode();
        final Object runNode = col.runNode;
        if (runNode != null && hash == col.runHash && node.equals(runNode)) {
            col.runLength++;
            return;
        }
        startRun(col, node, hash);
        converter.encodeIri(col, node);
    }

    /**
     * Adds a cell to the object column of RDF statements.
     *
     * @param col the column
     * @param node the object
     */
    public void addObjectCell(Column<TNode> col, TNode node) {
        if (node == null) {
            throw missingTerm(col);
        }
        final int hash = node.hashCode();
        final Object runNode = col.runNode;
        if (runNode != null && hash == col.runHash && node.equals(runNode)) {
            col.runLength++;
            return;
        }
        startRun(col, node, hash);
        converter.encodeAny(col, node);
    }

    private static RdfProtoSerializationError missingTerm(Column<?> col) {
        return new RdfProtoSerializationError("The %s of a statement cannot be null.".formatted(col.position));
    }

    /** Ends the active run and starts a new one with a bound node. The caller encodes the node. */
    private void startRun(Column<TNode> col, TNode node, int hash) {
        finalizeRun(col);
        col.runLength = 1;
        col.runNode = node;
        col.runHash = hash;
    }

    /**
     * Adds a cell to a column of graph names: a node that the converter describes as the default
     * graph becomes an unbound cell.
     *
     * @param col the column
     * @param node the graph name, or null for the default graph
     */
    public void addGraphCell(Column<TNode> col, TNode node) {
        if (node == null) {
            addUnbound(col);
            return;
        }
        final int hash = node.hashCode();
        final Object runNode = col.runNode;
        if (runNode != null && hash == col.runHash && node.equals(runNode)) {
            col.runLength++;
            return;
        }
        // The value lists and the layout do not depend on each other, so the value can be
        // encoded before the run is ended – and if it turns out to be the default graph, the
        // cell just extends an unbound run.
        col.defaultGraphSeen = false;
        converter.encodeGraph(col, node);
        if (col.defaultGraphSeen) {
            addUnbound(col);
            return;
        }
        startRun(col, node, hash);
    }

    private void addUnbound(Column<TNode> col) {
        if (col.runLength > 0 && col.runNode == null) {
            col.runLength++;
            return;
        }
        finalizeRun(col);
        col.runLength = 1;
    }

    private void finalizeRun(Column<TNode> col) {
        if (col.runLength == 0) {
            return;
        }
        if (col.runNode == null) {
            emitException(col, true, col.runLength - 1);
        } else if (col.runLength >= 2) {
            emitException(col, false, col.runLength - 2);
        } else {
            col.skip++;
        }
        col.runLength = 0;
        col.runNode = null;
    }

    private static void emitException(Column<?> col, boolean unbound, int len) {
        final int lenCode = Math.min(len, MAX_INLINE_LEN);
        col.layout.add((col.skip << TOKEN_SKIP_SHIFT) | (unbound ? TOKEN_UNBOUND : 0) | lenCode);
        if (lenCode == MAX_INLINE_LEN) {
            col.layout.add(len - MAX_INLINE_LEN);
        }
        col.skip = 0;
    }

    // *** Lookup ids of the values ***

    private void markIriUsed(int nameId, int prefixId) {
        markUsed(usedNames, nameId);
        if (prefixId != 0) {
            markUsed(usedPrefixes, prefixId);
        }
    }

    private int makeDatatypeId(String datatype) {
        final int id = lookupEncoder.makeDatatypeId(datatype);
        markUsed(usedDatatypes, id);
        return id;
    }

    private static void reserveUsedIds(long[] used, int tableSize, int count) {
        if (tableSize != 0 && count > 0) {
            used[0] -= count;
        }
    }

    private void checkBaseDirectionsAllowed() {
        if (rdfVersion == RdfVersion.RDF_VERSION_1_1) {
            throw new RdfProtoSerializationError(
                "The stream declares RDF 1.1, which does not allow literals with a base direction."
            );
        }
    }

    private void checkTripleTermsAllowed() {
        if (rdfVersion == RdfVersion.RDF_VERSION_1_1 || rdfVersion == RdfVersion.RDF_VERSION_1_2_BASIC) {
            throw new RdfProtoSerializationError(
                "The stream declares %s, which does not allow triple terms.".formatted(
                    rdfVersion == RdfVersion.RDF_VERSION_1_1 ? "RDF 1.1" : "RDF 1.2 Basic"
                )
            );
        }
    }

    // *** Triple terms ***

    /** Adds a triple term as the value of a new run of the column. */
    private void addTripleTerm(Column<TNode> col, TNode s, TNode p, TNode o) {
        if ((col.allowedTerms & (1 << TERM_TRIPLE)) == 0) {
            // Checked here too, so that the triple term is not encoded for nothing
            throw col.notAllowedTerm(TERM_TRIPLE);
        }
        checkTripleTermsAllowed();
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
            reserveUsedIds(usedNames, maxNameTableSize, extra);
            reserveUsedIds(usedPrefixes, maxPrefixTableSize, extra);
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

        private final Column<TNode> col;
        // Nesting depth of the term being encoded, 1 for the top level
        private int depth;
        // The message of the term encoded last: RdfIri, String (blank node), RdfLiteral or
        // RdfTripleTerm
        private Object last;
        // Number of IRIs in the whole triple term
        int iriCount = 0;

        TripleTermEncoder(Column<TNode> col) {
            this.col = col;
        }

        RdfTripleTerm.Mutable encode(TNode s, TNode p, TNode o, int depth) {
            if (depth > MAX_TRIPLE_TERM_DEPTH) {
                throw new RdfProtoSerializationError(
                    "Triple terms nested deeper than %d levels are not supported.".formatted(MAX_TRIPLE_TERM_DEPTH)
                );
            }
            final RdfTripleTerm.Mutable term = RdfTripleTerm.newInstance();
            this.depth = depth;
            last = null;
            converter.encodeResource(this, s);
            if (last instanceof RdfIri iri) {
                term.setSIri(iri);
            } else if (last instanceof String bnode) {
                term.setSBnode(bnode);
            } else {
                throw new RdfProtoSerializationError(
                    "The subject of a triple term must be an IRI or a blank node, got: %s".formatted(s)
                );
            }
            this.depth = depth;
            last = null;
            converter.encodeIri(this, p);
            if (!(last instanceof RdfIri predicate)) {
                throw new RdfProtoSerializationError(
                    "The predicate of a triple term must be an IRI, got: %s".formatted(p)
                );
            }
            term.setPIri(predicate);
            this.depth = depth;
            last = null;
            converter.encodeAny(this, o);
            if (last instanceof RdfIri iri) {
                term.setOIri(iri);
            } else if (last instanceof String bnode) {
                term.setOBnode(bnode);
            } else if (last instanceof RdfLiteral literal) {
                term.setOLiteral(literal);
            } else if (last instanceof RdfTripleTerm triple) {
                term.setOTripleTerm(triple);
            } else {
                throw new RdfProtoSerializationError("Unsupported object of a triple term: %s".formatted(o));
            }
            return term;
        }

        @Override
        public void iri(String iri) {
            final long ids = lookupEncoder.makeIriIds(iri);
            final int nameId = (int) (ids >>> 32);
            final int prefixId = (int) ids;
            markIriUsed(nameId, prefixId);
            // The IRIs of the column's triple terms have an inference state of their own
            final ColumnExtras extras = col.extras();
            final int storedNameId = nameId == extras.tripleLastNameId + 1 ? 0 : nameId;
            final int storedPrefixId = prefixId == extras.tripleLastPrefixId ? 0 : prefixId;
            extras.tripleLastNameId = nameId;
            extras.tripleLastPrefixId = prefixId;
            iriCount++;
            last = RdfIri.newInstance().setPrefixId(storedPrefixId).setNameId(storedNameId);
        }

        @Override
        public void blankNode(String label) {
            last = label;
        }

        @Override
        public void simpleLiteral(String lex) {
            last = RdfLiteral.newInstance().setLex(lex);
        }

        @Override
        public void langLiteral(String lex, String lang) {
            last = RdfLiteral.newInstance().setLex(lex).setLangtag(lang);
        }

        @Override
        public void dirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
            checkBaseDirectionsAllowed();
            last = RdfLiteral.newInstance().setLex(lex).setLangtag(lang).setDirection(direction);
        }

        @Override
        public void dtLiteral(String lex, String datatype) {
            last = RdfLiteral.newInstance().setLex(lex).setDatatype(makeDatatypeId(datatype));
        }

        @Override
        public void tripleTerm(TNode s, TNode p, TNode o) {
            final int outer = depth;
            final RdfTripleTerm.Mutable nested = encode(s, p, o, outer + 1);
            depth = outer;
            last = nested;
        }

        @Override
        public void defaultGraph() {
            throw new RdfProtoSerializationError("The default graph cannot occur in a triple term.");
        }
    }

    // *** Namespace declarations (Jelly-RDF) ***

    /**
     * Encodes the IRI of a namespace declaration. The IRIs of the declarations of a frame have an
     * inference state of their own, which advances through them in order and resets with the
     * frame. Uses the lookup budget like a row does, so check {@link #hasRoom()} first.
     *
     * @param namespace the IRI node of the namespace
     * @return the encoded IRI
     */
    public RdfIri encodeNamespaceIri(TNode namespace) {
        namespaceEncoder.namespace = namespace;
        namespaceEncoder.iri = null;
        converter.encodeIri(namespaceEncoder, namespace);
        if (namespaceEncoder.iri == null) {
            throw namespaceEncoder.notAnIri();
        }
        return namespaceEncoder.iri;
    }

    /** NodeEncoder for the IRIs of namespace declarations, which accepts nothing else. */
    private final class NamespaceEncoder implements NodeEncoder<TNode> {

        // The node being encoded, and its IRI
        TNode namespace = null;
        RdfIri iri = null;

        RdfProtoSerializationError notAnIri() {
            return new RdfProtoSerializationError(
                "The namespace of a declaration must be an IRI, got: %s".formatted(namespace)
            );
        }

        @Override
        public void iri(String value) {
            final long ids = lookupEncoder.makeIriIds(value);
            final int nameId = (int) (ids >>> 32);
            final int prefixId = (int) ids;
            markIriUsed(nameId, prefixId);
            final int storedNameId = nameId == namespaceLastNameId + 1 ? 0 : nameId;
            final int storedPrefixId = prefixId == namespaceLastPrefixId ? 0 : prefixId;
            namespaceLastNameId = nameId;
            namespaceLastPrefixId = prefixId;
            iri = RdfIri.newInstance().setPrefixId(storedPrefixId).setNameId(storedNameId);
        }

        @Override
        public void blankNode(String label) {
            throw notAnIri();
        }

        @Override
        public void simpleLiteral(String lex) {
            throw notAnIri();
        }

        @Override
        public void langLiteral(String lex, String lang) {
            throw notAnIri();
        }

        @Override
        public void dirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
            throw notAnIri();
        }

        @Override
        public void dtLiteral(String lex, String datatype) {
            throw notAnIri();
        }

        @Override
        public void tripleTerm(TNode s, TNode p, TNode o) {
            throw notAnIri();
        }

        @Override
        public void defaultGraph() {
            throw notAnIri();
        }
    }

    // *** Lookup entries – called by the lookup encoder ***

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

    // *** Used lookup ids ***

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

    private static long usedIdsBudget(int tableSize, int columns) {
        // If table size is 0, then it's not used (does not constrain us)
        if (tableSize == 0) {
            return Long.MAX_VALUE;
        }
        // Floor at 0 to avoid a situation where there are more columns than slots
        // in e.g., datatype table, which causes the budget to be exhausted on the
        // first row.
        return Math.max(tableSize - columns, 0);
    }

    /**
     * Registers a lookup entry assigned by the current frame. Assigning an id that the frame
     * already touched means the entry would be overwritten while the frame still refers to it.
     * <p>
     * The owner ends a frame before it can get that far (see hasRoom), so this can only fire for a
     * single row that does not fit in the lookup tables at all – no framing decision can help there.
     */
    private static void checkAndMarkUsed(long[] used, int id, String kind) {
        if (isUsed(used, id)) {
            throw new RdfProtoSerializationError(
                (
                    "The %s lookup table is too small to encode a single row: " +
                    "entry %d would be overwritten while still referenced in the current frame. " +
                    "Increase the max %s table size."
                ).formatted(kind, id, kind)
            );
        }
        markUsed(used, id);
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
}
