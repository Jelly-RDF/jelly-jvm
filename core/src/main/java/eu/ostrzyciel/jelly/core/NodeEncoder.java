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
        // 1: datatypes and IRI names
        // The pointer is the index in the lookup table, the serial is the serial number of the entry.
        // The serial in the lookup table must be equal to the serial here for the entry to be valid.
        public int lookupPointer1;
        public int lookupSerial1;
        // 2: IRI prefixes
        public int lookupPointer2;
        public int lookupSerial2;
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
    private final EncoderLookup prefixLookup;
    private final EncoderLookup nameLookup;

    // We split the node caches in three – the first two are for nodes that depend on the lookups
    // (IRIs and datatype literals). The third one is for nodes that don't depend on the lookups.
    private final NodeCache<Object, DependentNode> iriNodeCache;
    private final NodeCache<Object, DependentNode> dtLiteralNodeCache;
    private final NodeCache<Object, UniversalTerm> nodeCache;

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
        datatypeLookup = new EncoderLookup(opt.maxDatatypeTableSize(), true);
        this.maxPrefixTableSize = opt.maxPrefixTableSize();
        if (maxPrefixTableSize > 0) {
            prefixLookup = new EncoderLookup(maxPrefixTableSize, true);
            iriNodeCache = new NodeCache<>(iriNodeCacheSize);
        } else {
            prefixLookup = null;
            iriNodeCache = null;
        }
        nameOnlyIris = new RdfIri[opt.maxNameTableSize() + 1];
        for (int i = 0; i < nameOnlyIris.length; i++) {
            nameOnlyIris[i] = new RdfIri(0, i);
        }
        dtLiteralNodeCache = new NodeCache<>(dtLiteralNodeCacheSize);
        nameLookup = new EncoderLookup(opt.maxNameTableSize(), maxPrefixTableSize > 0);
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
                cachedNode.lookupSerial1 == datatypeLookup.serials[cachedNode.lookupPointer1]
        ) {
            datatypeLookup.onAccess(cachedNode.lookupPointer1);
            return cachedNode.encoded;
        }

        // The node is not encoded, but we may already have the datatype encoded
        Integer dtEntry = datatypeLookup.getEntry(datatypeName);
        int dtId;
        if (dtEntry == null) {
            var newDtEntry = datatypeLookup.addEntry(datatypeName);
            rowsBuffer.append(new RdfStreamRow(
                new RdfDatatypeEntry(newDtEntry.setId, datatypeName)
            ));
            dtId = newDtEntry.getId;
        } else {
            dtId = dtEntry.intValue();
            datatypeLookup.onAccess(dtId);
        }
        cachedNode.lookupPointer1 = dtId;
        cachedNode.lookupSerial1 = datatypeLookup.serials[dtId];
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
        if (maxPrefixTableSize == 0) {
            // Fast path for no prefixes
            Integer nameEntry = nameLookup.getEntry(iri);
            int nameId;
            if (nameEntry == null) {
                var newNameEntry = nameLookup.addEntry(iri);
                rowsBuffer.append(new RdfStreamRow(
                        new RdfNameEntry(newNameEntry.setId, iri)
                ));
                nameId = newNameEntry.getId;
            } else {
                nameId = nameEntry.intValue();
                nameLookup.onAccess(nameId);
            }
            // Branchless version of:
            // int nameIndex = lastIriNameId + 1 == nameId ? 0 : nameId;
            int nameIndex = ((-((lastIriNameId + 1) ^ nameId)) >> 31) & nameId;
            lastIriNameId = nameId;
            return nameOnlyIris[nameIndex];
        }

        // Slow path, with splitting out the prefix
        var cachedNode = iriNodeCache.computeIfAbsent(iri, k -> new DependentNode());
        // Check if the value is still valid
        if (cachedNode.encoded != null &&
                cachedNode.lookupSerial1 == nameLookup.serials[cachedNode.lookupPointer1] &&
                cachedNode.lookupSerial2 == prefixLookup.serials[cachedNode.lookupPointer2]
        ) {
            nameLookup.onAccess(cachedNode.lookupPointer1);
            prefixLookup.onAccess(cachedNode.lookupPointer2);
            return outputIri(cachedNode);
        }

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

        Integer prefixEntry = prefixLookup.getEntry(prefix);
        int prefixId;
        if (prefixEntry == null) {
            var newPrefixEntry = prefixLookup.addEntry(prefix);
            rowsBuffer.append(new RdfStreamRow(
                    new RdfPrefixEntry(newPrefixEntry.setId, prefix)
            ));
            prefixId = newPrefixEntry.getId;
        } else {
            prefixId = prefixEntry.intValue();
            prefixLookup.onAccess(prefixId);
        }

        Integer nameEntry = nameLookup.getEntry(postfix);
        int nameId;
        if (nameEntry == null) {
            var newNameEntry = nameLookup.addEntry(postfix);
            rowsBuffer.append(new RdfStreamRow(
                    new RdfNameEntry(newNameEntry.setId, postfix)
            ));
            nameId = newNameEntry.getId;
        } else {
            nameId = nameEntry.intValue();
            nameLookup.onAccess(nameId);
        }

        cachedNode.lookupPointer1 = nameId;
        cachedNode.lookupSerial1 = nameLookup.serials[nameId];
        cachedNode.lookupPointer2 = prefixId;
        cachedNode.lookupSerial2 = prefixLookup.serials[prefixId];
        cachedNode.encoded = new RdfIri(prefixId, nameId);
        return outputIri(cachedNode);
    }

    /**
     * Helper function to output an IRI from a cached node using same-prefix and next-name optimizations.
     * @param cachedNode The cached node
     * @return The encoded IRI
     */
    private UniversalTerm outputIri(DependentNode cachedNode) {
        int nameId = cachedNode.lookupPointer1;
        int prefixId = cachedNode.lookupPointer2;
        if (lastIriPrefixId == prefixId) {
            // Branchless version of:
            // int nameIndex = lastIriNameId + 1 == nameId ? 0 : nameId;
            int nameIndex = ((-((lastIriNameId + 1) ^ nameId)) >> 31) & nameId;
            lastIriNameId = nameId;
            return nameOnlyIris[nameIndex];
        } else {
            lastIriPrefixId = prefixId;
            // Here using branchless won't help :(
            if (lastIriNameId + 1 == nameId) {
                lastIriNameId = nameId;
                return new RdfIri(prefixId, 0);
            } else {
                lastIriNameId = nameId;
                return cachedNode.encoded;
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
