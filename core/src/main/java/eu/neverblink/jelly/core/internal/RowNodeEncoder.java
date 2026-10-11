package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NodeEncoder;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.RdfBufferAppender;
import eu.neverblink.jelly.core.RdfProtoSerializationError;
import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Encodes RDF terms into the proto messages of the row layout (Jelly-RDF 1.0 and 1.1, Jelly-Patch):
 * RdfIri, RdfLiteral, RdfTriple, a String for a blank node, or RdfDefaultGraph.
 * <p>
 * Call one of the encode* methods with a term: it asks the converter to describe the term to this
 * encoder, and returns the message. It is absolutely NOT
 * thread-safe, and should only be ever used by a single encoder.
 *
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
@InternalApi
public final class RowNodeEncoder<TNode> implements NodeEncoder<TNode> {

    /**
     * A direct-mapped cache for already encoded literals: the hash picks one slot, and a colliding
     * key takes it over. No eviction bookkeeping and no per-entry object, which makes it ~2x faster
     * than a LinkedHashMap at a similar hit rate on real data. See NodeCacheBench.
     */
    private static final class LiteralCache {

        private final Object[] keys;
        private final RdfLiteral[] values;
        private final int mask;

        LiteralCache(int minSize) {
            final int size = LookupEncoder.tableSizeFor(minSize);
            this.keys = new Object[size];
            this.values = new RdfLiteral[size];
            this.mask = size - 1;
        }

        RdfLiteral get(Object key) {
            final int slot = LookupEncoder.spread(key) & mask;
            return key.equals(keys[slot]) ? values[slot] : null;
        }

        void put(Object key, RdfLiteral value) {
            final int slot = LookupEncoder.spread(key) & mask;
            keys[slot] = key;
            values[slot] = value;
        }
    }

    /** A cached literal with a datatype, and the serial number of its datatype id. */
    private static final class DtLiteral {

        Object key;
        int keyHash;
        RdfLiteral literal;
        int serial;
    }

    /**
     * Literals with a datatype, keyed by their node. 2-way set associative like the IRI cache of
     * {@link LookupEncoder}: datatype literals are many, and a miss costs a datatype lookup. An
     * entry is only valid while the serial number of its datatype id is unchanged.
     */
    private static final class DtLiteralCache {

        private final DtLiteral[] nodes;
        private final int mask;

        DtLiteralCache(int minSize) {
            final int size = LookupEncoder.tableSizeFor(minSize);
            this.nodes = new DtLiteral[size];
            // A set is two adjacent slots, so there are half as many sets as slots.
            this.mask = (size >> 1) - 1;
        }

        private static boolean holds(DtLiteral node, Object key, int hash) {
            return node != null && node.keyHash == hash && key.equals(node.key);
        }

        /** The entry for a key, most recent first in its set. A new entry has no literal. */
        DtLiteral get(Object key) {
            final int hash = LookupEncoder.spread(key);
            final int slot = (hash & mask) << 1;
            final var first = nodes[slot];
            if (holds(first, key, hash)) {
                return first;
            }
            final var other = nodes[slot + 1];
            final DtLiteral node;
            if (holds(other, key, hash)) {
                node = other;
            } else {
                node = other == null ? new DtLiteral() : other;
                node.literal = null;
                node.key = key;
                node.keyHash = hash;
            }
            nodes[slot + 1] = first;
            return (nodes[slot] = node);
        }
    }

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    static final RdfIri zeroIri = RdfIri.newInstance();

    private final ProtoEncoderConverter<TNode> converter;
    private final LookupEncoder lookups;

    // IRI inference state: the ids of the previous IRI
    private int lastIriNameId = 0;
    private int lastIriPrefixId;

    // By name id: the IRI with prefix 0. The IRIs with both ids are kept in the lookup encoder's
    // cache entries.
    private final RdfIri[] nameOnlyIris;

    private final DtLiteralCache dtLiteralCache;
    // Simple literals, keyed by their lexical form, and language-tagged literals, keyed by their node
    private final LiteralCache otherLiteralCache;

    // The term being encoded – the key of the literal caches
    private TNode node;
    // The message of the term encoded last
    private Object encoded;

    /**
     * Creates a new RowNodeEncoder.
     *
     * @param converter the converter of the RDF library
     * @param bufferAppender receiver of the new lookup entries
     * @param maxPrefixTableSize the size of the prefix table, 0 if disabled
     * @param maxNameTableSize the size of the name table
     * @param maxDatatypeTableSize the size of the datatype table
     */
    public RowNodeEncoder(
        ProtoEncoderConverter<TNode> converter,
        RdfBufferAppender bufferAppender,
        int maxPrefixTableSize,
        int maxNameTableSize,
        int maxDatatypeTableSize
    ) {
        this.converter = converter;
        this.lookups = LookupEncoder.create(bufferAppender, maxPrefixTableSize, maxNameTableSize, maxDatatypeTableSize);
        // With a prefix table, the first IRI must state its prefix. Without one, the prefix id is
        // always 0, so it never has to be stated.
        this.lastIriPrefixId = maxPrefixTableSize > 0 ? -1000 : 0;
        this.nameOnlyIris = new RdfIri[maxNameTableSize + 1];
        final int literalCacheSize = Math.clamp(maxNameTableSize, 256, 1024);
        this.dtLiteralCache = new DtLiteralCache(literalCacheSize);
        this.otherLiteralCache = new LiteralCache(literalCacheSize);
    }

    // *** Called by the encoders ***

    /**
     * Encodes a term of any kind.
     * @return RdfIri, String (blank node), RdfLiteral or RdfTriple
     */
    public Object encodeAny(TNode node) {
        this.node = node;
        encoded = null;
        converter.encodeAny(this, node);
        return encoded;
    }

    /**
     * Encodes a term that should be an IRI (a predicate, or the IRI of a namespace).
     * @return RdfIri, or another message if it is not an IRI
     */
    public Object encodeIri(TNode node) {
        this.node = node;
        encoded = null;
        converter.encodeIri(this, node);
        return encoded;
    }

    /**
     * Encodes a term that should be an IRI or a blank node (a subject).
     * @return RdfIri or String (blank node), or another message if it is neither
     */
    public Object encodeResource(TNode node) {
        this.node = node;
        encoded = null;
        converter.encodeResource(this, node);
        return encoded;
    }

    /**
     * Encodes a graph name.
     * @param node the graph name, or null for the default graph
     * @return RdfIri, String (blank node), RdfDefaultGraph or RdfLiteral
     */
    public Object encodeGraph(TNode node) {
        this.node = node;
        encoded = null;
        converter.encodeGraph(this, node);
        if (encoded instanceof RdfTriple) {
            throw new RdfProtoSerializationError(
                "Cannot encode graph node: %s. A triple term cannot be a graph name.".formatted(node)
            );
        }
        return encoded;
    }

    /**
     * Starts a new statement (or other stream row): until the next call, the lookup entries it
     * uses are not evicted.
     */
    public void newEpoch() {
        lookups.newEpoch();
    }

    // *** NodeEncoder – called by the converter ***

    @Override
    public void iri(String iri) {
        final int lastNameId = lastIriNameId;
        if (!lookups.hasPrefixTable()) {
            // The prefix id is always 0
            final int nameId = lookups.makeNameId(iri);
            lastIriNameId = nameId;
            encoded = lastNameId + 1 == nameId ? zeroIri : nameOnlyIri(nameId);
            return;
        }
        final LookupEncoder.IriIds ids = lookups.makeIriIdsEntry(iri);
        final int nameId = ids.nameId;
        final int prefixId = ids.prefixId;
        lastIriNameId = nameId;
        // Same prefix as the previous IRI: prefix id 0. Next name after the previous IRI's: name id 0.
        if (lastIriPrefixId == prefixId) {
            encoded = lastNameId + 1 == nameId ? zeroIri : nameOnlyIri(nameId);
        } else {
            lastIriPrefixId = prefixId;
            encoded = lastNameId + 1 == nameId ? RdfIri.newInstance().setPrefixId(prefixId) : fullIri(ids);
        }
    }

    /** The prefix-0 IRI for a name id, created on first use. */
    private RdfIri nameOnlyIri(int nameId) {
        final var iri = nameOnlyIris[nameId];
        if (iri != null) {
            return iri;
        }
        return (nameOnlyIris[nameId] = RdfIri.newInstance().setNameId(nameId));
    }

    /** The IRI with both ids, created on first use. */
    private static RdfIri fullIri(LookupEncoder.IriIds ids) {
        final var iri = ids.rowIri;
        if (iri != null) {
            return iri;
        }
        return (ids.rowIri = RdfIri.newInstance().setPrefixId(ids.prefixId).setNameId(ids.nameId));
    }

    @Override
    public void blankNode(String label) {
        encoded = label;
    }

    @Override
    public void simpleLiteral(String lex) {
        var literal = otherLiteralCache.get(lex);
        if (literal == null) {
            literal = RdfLiteral.newInstance().setLex(lex);
            otherLiteralCache.put(lex, literal);
        }
        encoded = literal;
    }

    @Override
    public void langLiteral(String lex, String lang) {
        var literal = otherLiteralCache.get(node);
        if (literal == null) {
            literal = RdfLiteral.newInstance().setLex(lex).setLangtag(lang);
            otherLiteralCache.put(node, literal);
        }
        encoded = literal;
    }

    @Override
    public void dirLangLiteral(String lex, String lang, RdfBaseDirection direction) {
        // The row layout has no base directions
        langLiteral(lex, lang);
    }

    @Override
    public void dtLiteral(String lex, String datatype) {
        final DtLiteral cached = dtLiteralCache.get(node);
        final RdfLiteral literal = cached.literal;
        if (literal != null && cached.serial == lookups.datatypeSerial(literal.getDatatype())) {
            lookups.onDatatypeAccess(literal.getDatatype());
            encoded = literal;
            return;
        }
        final int datatypeId = lookups.makeDatatypeId(datatype);
        cached.literal = RdfLiteral.newInstance().setLex(lex).setDatatype(datatypeId);
        cached.serial = lookups.datatypeSerial(datatypeId);
        encoded = cached.literal;
    }

    @Override
    public void tripleTerm(TNode s, TNode p, TNode o) {
        final RdfTriple.Mutable triple = RdfTriple.newInstance();
        triple.setSubject(encodeResource(s));
        triple.setPredicate(encodeIri(p));
        triple.setObject(encodeAny(o));
        encoded = triple;
    }

    @Override
    public void defaultGraph() {
        encoded = RdfDefaultGraph.EMPTY;
    }
}
