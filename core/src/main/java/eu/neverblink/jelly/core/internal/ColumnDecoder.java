package eu.neverblink.jelly.core.internal;

import static eu.neverblink.jelly.core.internal.ColumnLayout.*;

import com.google.protobuf.ByteString;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NameDecoder;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection;
import eu.neverblink.jelly.core.proto.v1.RdfColumn;
import eu.neverblink.jelly.core.proto.v1.RdfIri;
import eu.neverblink.jelly.core.proto.v1.RdfLiteral;
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked;
import eu.neverblink.jelly.core.proto.v1.RdfTripleTerm;
import eu.neverblink.jelly.core.proto.v1.RdfVersion;
import eu.neverblink.jelly.core.utils.RdfVersionUtils;
import eu.neverblink.protoc.java.runtime.MessageCollection;
import eu.neverblink.protoc.java.runtime.RepeatedInt;
import eu.neverblink.protoc.java.runtime.RepeatedString;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Decoder of RdfColumn messages, shared by the column layout of Jelly-RDF and by Jelly-SPARQL.
 * <p>
 * A column is read with a {@link Cursor}, which
 * returns its cells in row order, in as many steps as the caller likes.
 *
 * @param <TNode> the type of RDF nodes in the library
 * @param <TDatatype> the type of RDF datatypes in the library
 */
@InternalApi
public final class ColumnDecoder<TNode, TDatatype> {

    private final DecoderBase<TNode, TDatatype> base;
    private final ProtoDecoderConverter<TNode, TDatatype> converter;

    // The RDF version declared by the stream, and the one supported by the reader (RdfVersion
    // numbers), which limit the terms the stream may contain
    private int declaredRdfVersion = RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE;
    private int supportedRdfVersion = RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE;

    /**
     * @param base the decoder whose lookups and converter are used
     */
    public ColumnDecoder(DecoderBase<TNode, TDatatype> base) {
        this.base = base;
        this.converter = base.converter;
    }

    /**
     * Sets the RDF versions that limit the terms of the stream.
     *
     * @param declared the RDF version declared by the stream options (RdfVersion number)
     * @param supported the highest RDF version the reader supports (RdfVersion number)
     */
    public void setRdfVersions(int declared, int supported) {
        this.declaredRdfVersion = declared;
        this.supportedRdfVersion = supported;
    }

    /**
     * Applies lookup entries, in the order name, prefix, datatype. In a packed entry only the
     * first value states its id, the rest are sequential.
     */
    public void applyLookupEntries(
        Iterable<RdfLookupEntryPacked> names,
        Iterable<RdfLookupEntryPacked> prefixes,
        Iterable<RdfLookupEntryPacked> datatypes
    ) {
        // Optimized lookup updates: get the decoder once and write to it in an
        // indexed loop. This can >2x faster than writing from an iterator naively.
        // See RdfLookupApplyBench.
        final NameDecoder<?> nameDecoder = base.getNameDecoder();
        for (final RdfLookupEntryPacked entry : names) {
            final RepeatedString values = entry.getValues();
            final int count = values.size();
            if (count > 0) {
                nameDecoder.updateNames(entry.getId(), values.get(0));
                for (int i = 1; i < count; i++) {
                    nameDecoder.updateNames(0, values.get(i));
                }
            }
        }
        for (final RdfLookupEntryPacked entry : prefixes) {
            final RepeatedString values = entry.getValues();
            final int count = values.size();
            if (count > 0) {
                nameDecoder.updatePrefixes(entry.getId(), values.get(0));
                for (int i = 1; i < count; i++) {
                    nameDecoder.updatePrefixes(0, values.get(i));
                }
            }
        }
        for (final RdfLookupEntryPacked entry : datatypes) {
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
                base.getDatatypeLookup().update(id, datatype);
                id = 0;
            }
        }
    }

    /**
     * Creates a decoder for a list of IRIs written as separate RdfIri messages, such as the
     * namespace declarations of a batch. In such a list, a prefix id of 0 means "the same prefix as
     * the IRI before", and a name id of 0 means "the name after the one of the IRI before".
     */
    public IriState newIriState() {
        return new IriState();
    }

    /**
     * Creates a cursor over the cells of a column.
     *
     * @param column the column
     * @param rows the number of rows (cells) of the column
     * @param allowUnbound whether the column may have unbound cells. If false, an unbound cell
     *                     is an error.
     * @param position the name of the column in error messages, e.g., "subject"
     */
    public Cursor cursor(RdfColumn column, int rows, boolean allowUnbound, String position) {
        return new Cursor(columnReader(column), column.getLayouts(), rows, allowUnbound, position);
    }

    /**
     * Picks the reader for a column: the reader for one term type if the column has no kinds, or
     * the reader for a column that mixes term types.
     */
    private ValueReader<TNode> columnReader(RdfColumn column) {
        if (!column.getKinds().isEmpty()) {
            return new MixedReader(column);
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
            return new TripleTermReader(column.getTripleTerms());
        }
        // IRIs, or no values at all
        return new IriReader(column);
    }

    /**
     * Cells of one column, returned in row order by {@link #fill(Object[], int, int)}.
     * <p>
     * Each layout token is checked as soon as it is read, against the values of the column and
     * the row count. Once all rows are filled, {@link #finish()} checks that nothing is left over.
     */
    public final class Cursor {

        private final ValueReader<TNode> reader;
        private final RepeatedInt layout;
        private final int layoutSize;
        private final int rows;
        private final boolean allowUnbound;
        private final String position;

        // Index of the next layout token
        private int k = 0;
        // Number of cells assigned by the tokens read so far (and the tail, once it is read)
        private long cellsAssigned = 0;
        // Values still to emit once each, from the current token (or the tail)
        private int skipLeft = 0;
        // Cells still to emit of the current run. The run starts once skipLeft is 0.
        private long runLeft = 0;
        // Whether the current run repeats a value, which is decoded when the run starts
        private boolean runRepeats = false;
        private boolean runStarted = false;
        private Object runNode = null;
        // Whether the tail (the values after the last token) has been read
        private boolean tailRead = false;

        private Cursor(ValueReader<TNode> reader, RepeatedInt layout, int rows, boolean allowUnbound, String position) {
            this.reader = reader;
            this.layout = layout;
            this.layoutSize = layout.size();
            this.rows = rows;
            this.allowUnbound = allowUnbound;
            this.position = position;
        }

        /**
         * Fills the next cells into {@code out}, from index {@code from} up to (exclusive)
         * {@code to}. The caller must not ask for more cells than the row count.
         */
        public void fill(Object[] out, int from, int to) {
            int pos = from;
            while (pos < to) {
                if (skipLeft > 0) {
                    final int n = Math.min(skipLeft, to - pos);
                    reader.decodeInto(out, pos, n);
                    pos += n;
                    skipLeft -= n;
                } else if (runLeft > 0) {
                    if (!runStarted) {
                        // The checks in nextToken() guarantee that the value is there
                        runNode = runRepeats ? reader.decodeNext() : null;
                        runStarted = true;
                    }
                    final int n = (int) Math.min(runLeft, to - pos);
                    Arrays.fill(out, pos, pos + n, runNode);
                    pos += n;
                    runLeft -= n;
                } else if (k < layoutSize) {
                    nextToken();
                } else if (!tailRead) {
                    // Implicit tail: all remaining values, once each
                    final int tail = reader.remaining();
                    if (tail > rows - cellsAssigned) {
                        throw moreCellsThanRows();
                    }
                    cellsAssigned += tail;
                    skipLeft = tail;
                    tailRead = true;
                } else {
                    // The rest of the cells, up to the row count, are unbound
                    if (!allowUnbound) {
                        throw unboundCell();
                    }
                    Arrays.fill(out, pos, to, null);
                    pos = to;
                }
            }
        }

        private void nextToken() {
            final int token = layout.get(k++);
            final int skip = token >>> TOKEN_SKIP_SHIFT;
            final boolean unbound = (token & TOKEN_UNBOUND) != 0;
            long len = token & MAX_INLINE_LEN;
            if (len == MAX_INLINE_LEN) {
                if (k >= layoutSize) {
                    throw new RdfProtoDeserializationError(
                        "Corrupt column layout: an escaped length token is not followed by an extension."
                    );
                }
                len = MAX_INLINE_LEN + Integer.toUnsignedLong(layout.get(k++));
            }
            if (skip > rows - cellsAssigned) {
                throw moreCellsThanRows();
            }
            // The values of the earlier tokens are all emitted by now
            if (skip > reader.remaining()) {
                throw new RdfProtoDeserializationError("Corrupt column layout: not enough values in the column.");
            }
            cellsAssigned += skip;
            skipLeft = skip;
            final long count = unbound ? len + 1 : len + 2;
            if (count > rows - cellsAssigned) {
                throw moreCellsThanRows();
            }
            if (unbound) {
                if (!allowUnbound) {
                    throw unboundCell();
                }
            } else if (reader.remaining() == skip) {
                throw new RdfProtoDeserializationError(
                    "Corrupt column layout: a repeat run points past the last value."
                );
            }
            cellsAssigned += count;
            runLeft = count;
            runRepeats = !unbound;
            runStarted = false;
        }

        /**
         * Checks that the layout and the values of the column are used up, after all rows were
         * filled.
         */
        public void finish() {
            if (skipLeft > 0 || runLeft > 0 || k < layoutSize || (!tailRead && reader.remaining() > 0)) {
                throw moreCellsThanRows();
            }
        }

        private RdfProtoDeserializationError unboundCell() {
            return new RdfProtoDeserializationError(
                "Corrupt column: the %s column must have a value in every row.".formatted(position)
            );
        }
    }

    private static RdfProtoDeserializationError moreCellsThanRows() {
        return new RdfProtoDeserializationError("Corrupt column layout: more cells than the frame row count.");
    }

    /**
     * Decodes a list of IRIs given as separate RdfIri messages, remembering the previous IRI's
     * prefix and name ids, which a 0 id refers to (see the RdfIri comments in the proto).
     */
    public final class IriState {

        // The lookup tables exist by the time a column is decoded
        private final NameDecoder<TNode> names = base.getNameDecoder();
        private int lastPrefixId = 0;
        private int lastNameId = 0;

        public TNode decode(int prefixId, int nameId) {
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

        public TNode decode(RdfIri iri) {
            return decode(iri.getPrefixId(), iri.getNameId());
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

        IriReader(RdfColumn column) {
            this.nameIds = column.getNameIds();
            this.prefixIds = column.getPrefixIds();
            final int prefixCount = prefixIds.size();
            if (prefixCount > 1 && prefixCount != nameIds.size()) {
                throw new RdfProtoDeserializationError(
                    "Corrupt column: %d prefix ids for %d name ids, expected 0, 1 or %d.".formatted(
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
     * Picks the reader for the literals of a column: one kind for all of them, or one per literal
     * (see the RdfColumn comments).
     */
    private ValueReader<TNode> literalReader(RdfColumn column) {
        final RepeatedString lexValues = column.getLexValues();
        final RepeatedInt kinds = column.getLiteralKinds();
        final int kindCount = kinds.size();
        if (kindCount > 1 && kindCount != lexValues.size()) {
            throw new RdfProtoDeserializationError(
                "Corrupt column: %d literal kinds for %d lexical forms, expected 0, 1 or %d.".formatted(
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
                "Corrupt column: %d base directions for %d language tags.".formatted(directions.size(), langtags.size())
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
            return base.getDatatypeLookup().get(datatypeIdOfKind(kind));
        }

        /** The index in langtags of a language kind (an even kind above 0). */
        int langtagIndex(int kind) {
            final int index = langtagIndexOfKind(kind);
            if (index >= langtags.size()) {
                throw new RdfProtoDeserializationError(
                    "Corrupt column: language tag %d referenced, but the column has %d.".formatted(
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
            if (isDatatypeKind(kind)) {
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
            if (isDatatypeKind(kind)) {
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
     * Converts a literal of a triple term, which is in the full form, with its base direction. This
     * is not the convertLiteral of DecoderBase, which converts the literals of the row layout of
     * Jelly-RDF, which have no base direction.
     */
    private TNode convertTripleTermLiteral(RdfLiteral literal) {
        final int direction = literal.getDirectionValue();
        switch (literal.getLiteralKindFieldNumber()) {
            case RdfLiteral.LANGTAG -> {
                if (direction == 0) {
                    return converter.makeLangLiteral(literal.getLex(), literal.getLangtag());
                }
                return converter.makeDirLangLiteral(literal.getLex(), literal.getLangtag(), baseDirection(direction));
            }
            case RdfLiteral.DATATYPE -> {
                if (direction != 0) {
                    throw directionWithoutLangtag();
                }
                return converter.makeDtLiteral(literal.getLex(), base.getDatatypeLookup().get(literal.getDatatype()));
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
        return declaredRdfVersion != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE
            ? declaredRdfVersion
            : supportedRdfVersion;
    }

    private RdfProtoDeserializationError notAllowed(String what) {
        final String version = RdfVersionUtils.rdfVersionName(allowedRdfVersion());
        return new RdfProtoDeserializationError(
            declaredRdfVersion != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE
                ? "The stream declares %s, but contains %s.".formatted(version, what)
                : "The stream contains %s, but this reader only supports %s.".formatted(what, version)
        );
    }

    /**
     * Reader for the triple terms of a column. Their IRIs take part in an IRI inference of their
     * own, separate from that of the column's IRIs, in the order subject, predicate, object (see
     * the RdfColumn comments).
     */
    private final class TripleTermReader extends ValueReader<TNode> {

        private final Iterator<RdfTripleTerm> terms;
        private final IriState iriState = new IriState();
        private int remaining;

        TripleTermReader(MessageCollection<RdfTripleTerm, ?> terms) {
            this.terms = terms.iterator();
            this.remaining = terms.size();
        }

        @Override
        int remaining() {
            return remaining;
        }

        @Override
        void decodeInto(Object[] out, int from, int count) {
            for (int i = 0; i < count; i++) {
                out[from + i] = decodeNext();
            }
        }

        @Override
        TNode decodeNext() {
            final int allowed = allowedRdfVersion();
            if (allowed != RdfVersion.RDF_VERSION_UNSPECIFIED_VALUE && allowed < RdfVersion.RDF_VERSION_1_2_VALUE) {
                throw notAllowed("triple terms");
            }
            remaining--;
            return decodeTripleTerm(terms.next());
        }

        /** Decodes a triple term. The nesting depth is already limited by the parser. */
        private TNode decodeTripleTerm(RdfTripleTerm triple) {
            final TNode s = switch (triple.getSubjectFieldNumber()) {
                case RdfTripleTerm.S_IRI -> iriState.decode(triple.getSIri());
                case RdfTripleTerm.S_BNODE -> converter.makeBlankNode(triple.getSBnode());
                default -> throw new RdfProtoDeserializationError("A triple term has no subject.");
            };
            if (triple.getPIri() == null) {
                throw new RdfProtoDeserializationError("A triple term has no predicate.");
            }
            final TNode p = iriState.decode(triple.getPIri());
            final TNode o = switch (triple.getObjectFieldNumber()) {
                case RdfTripleTerm.O_IRI -> iriState.decode(triple.getOIri());
                case RdfTripleTerm.O_BNODE -> converter.makeBlankNode(triple.getOBnode());
                case RdfTripleTerm.O_LITERAL -> convertTripleTermLiteral(triple.getOLiteral());
                case RdfTripleTerm.O_TRIPLE_TERM -> decodeTripleTerm(triple.getOTripleTerm());
                default -> throw new RdfProtoDeserializationError("A triple term has no object.");
            };
            return converter.makeTripleNode(s, p, o);
        }
    }

    /**
     * Reader for a column whose values mix term types: the kinds field says which list holds the
     * next value (see the RdfColumn comments).
     */
    private final class MixedReader extends ValueReader<TNode> {

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
        // Readers of the values of each type, null where the column has none
        private final ValueReader<TNode> iris;
        private final ValueReader<TNode> literals;
        private final ValueReader<TNode> bnodes;
        private final ValueReader<TNode> tripleTerms;
        private int index = 0;

        MixedReader(RdfColumn column) {
            final int iriCount = column.getNameIds().size();
            final int literalCount = column.getLexValues().size();
            final int bnodeCount = column.getBnodes().size();
            final int tripleCount = column.getTripleTerms().size();
            this.valueCount = iriCount + literalCount + bnodeCount + tripleCount;
            final ByteString kindsField = column.getKinds();
            if (kindsField.size() != (valueCount + 3) >>> 2) {
                throw new RdfProtoDeserializationError(
                    "Corrupt column: %d bytes of kinds for %d values.".formatted(kindsField.size(), valueCount)
                );
            }
            // A copy, which is read faster than the ByteString, and a quarter of the value count long
            this.kinds = kindsField.toByteArray();
            // Check that the kinds agree with the lists of values, so that decoding cannot run out
            // of values in one of them. The bytes that hold 4 values are counted by the table,
            // 16383 at most at a time, so that no 16-bit lane overflows.
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
                counts[TERM_IRI] != iriCount ||
                counts[TERM_LITERAL] != literalCount ||
                counts[TERM_BNODE] != bnodeCount ||
                counts[TERM_TRIPLE] != tripleCount
            ) {
                throw new RdfProtoDeserializationError(
                    "Corrupt column: the kinds do not match the number of values of each type."
                );
            }
            final int unusedBits = (valueCount & 3) == 0 ? 0 : 8 - ((valueCount & 3) << 1);
            if (unusedBits != 0 && (kinds[kinds.length - 1] & 0xff) >>> (8 - unusedBits) != 0) {
                throw new RdfProtoDeserializationError("Corrupt column: unused bits of the kinds are not 0.");
            }
            this.iris = iriCount == 0 ? null : new IriReader(column);
            this.literals = literalCount == 0 ? null : literalReader(column);
            this.bnodes = bnodeCount == 0 ? null : new BnodeReader(column.getBnodes());
            this.tripleTerms = tripleCount == 0 ? null : new TripleTermReader(column.getTripleTerms());
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
            // The counts were checked up front, so the reader always has the value
            return switch (kindAt(index++)) {
                case TERM_IRI -> iris.decodeNext();
                case TERM_LITERAL -> literals.decodeNext();
                case TERM_BNODE -> bnodes.decodeNext();
                default -> tripleTerms.decodeNext();
            };
        }
    }
}
