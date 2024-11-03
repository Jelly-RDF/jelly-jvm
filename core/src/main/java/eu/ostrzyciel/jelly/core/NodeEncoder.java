package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.*;
import scala.collection.mutable.ArrayBuffer;

import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * Encodes RDF nodes native to the used RDF library (e.g., Apache Jena, RDF4J) into Jelly's protobuf objects.
 * This class performs a lot of caching to avoid encoding the same node multiple times. It is absolutely NOT
 * thread-safe, and should only be ever used by a single instance of ProtoEncoder.
 * 
 * <p>
 * This class is marked as public because make* methods in ProtoEncoder are inlined, and the inlining
 * requires the NodeEncoder to be public. Do NOT use this class outside of ProtoEncoder. It is not
 * considered part of the public API.
 * </p>
 * @param <TNode> The type of RDF nodes used by the RDF library.
 */
public final class NodeEncoder<TNode> {
    /**
     * A cached node that depends on other lookups (RdfIri and RdfLiteral in the datatype variant).
     */
    static final class DependentNode {
        // The actual cached node
        public UniversalTerm encoded;
        // datatypes and IRI prefixes
        // The pointer is the index in the lookup table, the serial is the serial number of the entry.
        // The serial in the lookup table must be equal to the serial here for the entry to be valid.
        public int lookupPointer;
        public int lookupSerial;
    }

    /**
     * A simple LRU cache for already encoded nodes.
     * @param <K> Key type
     * @param <V> Value type
     */
    private static final class NodeCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public NodeCache(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    private final int maxPrefixTableSize;
    private int lastIriNameId;
    private int lastIriPrefixId = -1000;

    private final EncoderLookup datatypeLookup;
    /**
     * The serial numbers of the entries, incremented each time the entry is replaced in the table.
     * This could theoretically overflow and cause bogus cache hits, but it's enormously
     * unlikely to happen in practice. I can buy a beer for anyone who can construct an RDF dataset that
     * causes this to happen.
     */
    private final int[] datatypeSerials;
    private final EncoderLookup prefixLookup;
    private final int[] prefixSerials;
    private final EncoderLookup nameLookup;

    // The last set value in the name lookup.
    // Used for rough deduplication of name entries for IRIs with same name but different prefixes.
    private String lastSetName;

    // We split the node caches in three – the first two are for nodes that depend on the lookups
    // (IRIs and datatype literals). The third one is for nodes that don't depend on the lookups.
    private final DependentNode[] iriNodeCache;
    private final NodeCache<Object, DependentNode> dtLiteralNodeCache;
    private final NodeCache<Object, UniversalTerm> nodeCache;

    // Pre-allocated IRI that has prefixId=0 and nameId=0
    static final RdfIri zeroIri = new RdfIri(0, 0);
    // Pre-allocated IRIs that have prefixId=0
    private final RdfIri[] nameOnlyIris;

    /**
     * Creates a new NodeEncoder.
     * @param opt Jelly RDF stream options
     * @param nodeCacheSize The size of the node cache (for nodes that don't depend on lookups)
     * @param iriNodeCacheSize The size of the IRI dependent node cache (for prefix+name encoding)
     * @param dtLiteralNodeCacheSize The size of the datatype literal dependent node cache
     */
    public NodeEncoder(RdfStreamOptions opt, int nodeCacheSize, int iriNodeCacheSize, int dtLiteralNodeCacheSize) {
        datatypeLookup = new EncoderLookup(opt.maxDatatypeTableSize());
        this.maxPrefixTableSize = opt.maxPrefixTableSize();
        if (maxPrefixTableSize > 0) {
            // With prefix table
            prefixLookup = new EncoderLookup(maxPrefixTableSize);
            prefixSerials = new int[opt.maxPrefixTableSize() + 1];
            iriNodeCache = new DependentNode[opt.maxNameTableSize() + 1];
            for (int i = 0; i < iriNodeCache.length; i++) {
                iriNodeCache[i] = new DependentNode();
            }
            nameOnlyIris = null;
        } else {
            // No prefix table
            prefixLookup = null;
            prefixSerials = null;
            iriNodeCache = null;
            // Pre-initialize a table of name-only IRIs
            nameOnlyIris = new RdfIri[opt.maxNameTableSize() + 1];
            for (int i = 0; i < nameOnlyIris.length; i++) {
                nameOnlyIris[i] = new RdfIri(0, i);
            }
        }
        dtLiteralNodeCache = new NodeCache<>(dtLiteralNodeCacheSize);
        datatypeSerials = new int[opt.maxDatatypeTableSize() + 1];
        nameLookup = new EncoderLookup(opt.maxNameTableSize());
        nodeCache = new NodeCache<>(nodeCacheSize);
    }

    /**
     * Encodes a datatype literal using two layers of caching – both for the entire literal, and the datatype name.
     * @param key The literal key (the unencoded literal node)
     * @param lex The lexical form of the literal
     * @param datatypeName The name of the datatype
     * @param rowsBuffer The buffer to which the new datatype entry should be appended
     * @return The encoded literal
     */
    public UniversalTerm encodeDtLiteral(
            TNode key, String lex, String datatypeName, ArrayBuffer<RdfStreamRow> rowsBuffer
    ) {
        var cachedNode = dtLiteralNodeCache.computeIfAbsent(key, k -> new DependentNode());
        // Check if the value is still valid
        if (cachedNode.encoded != null &&
                cachedNode.lookupSerial == datatypeSerials[cachedNode.lookupPointer]
        ) {
            // Touch the datatype so that it doesn't get evicted
            datatypeLookup.onAccess(cachedNode.lookupPointer);
            return cachedNode.encoded;
        }

        // The node is not encoded, but we may already have the datatype encoded
        var dtEntry = datatypeLookup.getOrAddEntry(datatypeName);
        if (dtEntry.newEntry) {
            rowsBuffer.append(new RdfStreamRow(
                new RdfDatatypeEntry(dtEntry.setId, datatypeName)
            ));
        }
        int dtId = dtEntry.getId;
        cachedNode.lookupPointer = dtId;
        cachedNode.lookupSerial = ++datatypeSerials[dtId];
        cachedNode.encoded = new RdfLiteral(
                lex, new RdfLiteral$LiteralKind$Datatype(dtId)
        );

        return cachedNode.encoded;
    }

    /**
     * Encodes an IRI using two layers of caching – both for the entire IRI, and the prefix and name tables.
     * @param iri The IRI to encode
     * @param rowsBuffer The buffer to which the new name and prefix lookup entries should be appended
     * @return The encoded IRI
     */
    public UniversalTerm encodeIri(String iri, ArrayBuffer<RdfStreamRow> rowsBuffer) {
        EncoderLookup.LookupEntry nameEntry;
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            nameEntry = nameLookup.getOrAddEntry(iri);
            if (nameEntry.newEntry) {
                rowsBuffer.append(new RdfStreamRow(
                        new RdfNameEntry(nameEntry.setId, iri)
                ));
            }
            if (lastIriNameId + 1 == nameEntry.getId) {
                lastIriNameId = nameEntry.getId;
                return zeroIri;
            } else {
                lastIriNameId = nameEntry.getId;
                return nameOnlyIris[lastIriNameId];
            }
        }

        // Slow path, with splitting out the prefix
        nameEntry = nameLookup.map.get(iri);

        int nameId;
        DependentNode cachedNode;
        if (nameEntry != null) {
            // The IRI is already in the cache, check if the value is still valid
            nameId = nameEntry.getId;
            cachedNode = iriNodeCache[nameId];
            int pId = cachedNode.lookupPointer;
            if (prefixSerials[pId] == cachedNode.lookupSerial) {
                prefixLookup.onAccess(pId);
                return outputIri(cachedNode.encoded, pId, nameId);
            }
        }

        // The IRI is not in the cache, or we are dealing with a duplicate entry.
        // In either case, we have to split the IRI in half.
        int i = iri.indexOf('#', 8);
        String prefix;
        String postfix;
        if (i == -1) {
            i = iri.lastIndexOf('/');
            if (i != -1) {
                prefix = iri.substring(0, i + 1);
                postfix = iri.substring(i + 1);
            } else {
                prefix = "";
                postfix = iri;
            }
        } else {
            prefix = iri.substring(0, i + 1);
            postfix = iri.substring(i + 1);
        }

        if (nameEntry != null) {
            // The entry is there, but our prefix got evicted -- need to update that
            nameId = nameEntry.getId;
            cachedNode = iriNodeCache[nameId];
        } else if (postfix.equals(lastSetName)) {
            // This is a duplicated entry -- same name, different prefix
            nameId = nameLookup.lastSetId;
            // Do not set the cachedNode here, we don't want to overwrite its contents
            cachedNode = null;
        } else {
            // No luck, need to add a whole new entry
            nameEntry = nameLookup.addEntry(iri);
            nameId = nameEntry.getId;
            cachedNode = iriNodeCache[nameId];
            lastSetName = postfix;
            rowsBuffer.append(new RdfStreamRow(
                    new RdfNameEntry(nameEntry.setId, postfix)
            ));
        }

        var prefixEntry = prefixLookup.getOrAddEntry(prefix);
        int prefixId = prefixEntry.getId;
        int serial;
        if (prefixEntry.newEntry) {
            rowsBuffer.append(new RdfStreamRow(
                new RdfPrefixEntry(prefixEntry.setId, prefix)
            ));
            serial = ++prefixSerials[prefixId];
        } else {
            serial = prefixSerials[prefixId];
        }

        if (cachedNode != null) {
            cachedNode.lookupPointer = prefixId;
            cachedNode.lookupSerial = serial;
            cachedNode.encoded = new RdfIri(prefixId, nameId);
            return outputIri(cachedNode.encoded, prefixId, nameId);
        }

        return outputIri(null, prefixId, nameId);
    }

    /**
     * Helper function to output an IRI from a cached node using same-prefix and next-name optimizations.
     * @param encoded The cached node -- optional, may be null (will be lazily constructed)
     * @param prefixId The prefix ID of the IRI
     * @param nameId The name ID of the IRI
     * @return The encoded IRI
     */
    private UniversalTerm outputIri(UniversalTerm encoded, int prefixId, int nameId) {
        if (lastIriPrefixId == prefixId) {
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return zeroIri;
            } else {
                lastIriNameId = nameId;
                return new RdfIri(0, nameId);
            }
        } else {
            lastIriPrefixId = prefixId;
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return new RdfIri(prefixId, 0);
            } else {
                lastIriNameId = nameId;
                if (encoded == null) {
                    return new RdfIri(prefixId, nameId);
                }
                return encoded;
            }
        }
    }

    /**
     * Encodes a node that is not an IRI or a datatype literal using a single layer of caching.
     * @param key The node key (the unencoded node)
     * @param encoder The function that encodes the node
     * @return The encoded node
     */
    public UniversalTerm encodeOther(Object key, Function<Object, UniversalTerm> encoder) {
        return nodeCache.computeIfAbsent(key, encoder);
    }
}
