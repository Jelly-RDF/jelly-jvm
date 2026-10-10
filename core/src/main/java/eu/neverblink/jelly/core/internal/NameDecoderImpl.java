package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NameDecoder;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import java.util.Arrays;
import java.util.function.Function;

/**
 * Class for decoding RDF IRIs from their Jelly representation.
 * @param <TIri> The type of the IRI in the target RDF library.
 */
@InternalApi
public final class NameDecoderImpl<TIri> implements NameDecoder<TIri> {

    // The stamp of "no prefix" (prefix id 0)
    private static final long NO_PREFIX = 0;
    // The stamp of a prefix entry that was never set
    private static final long UNSET_PREFIX = -2;
    // The stamp of a cached IRI that is not there: no prefix entry ever has it
    private static final long NO_IRI = -1;

    private static final class NameLookupEntry {

        // Primary: the actual name
        public String name;
        // The last IRI made with this name, and the stamp of the prefix it was made with (see
        // prefixStamps). Valid while the prefix entry still has that stamp.
        public long lastStamp = NO_IRI;
        public Object lastIri;
        // The IRI made with the prefix before the last one: names used with two prefixes in turn
        // are common.
        public long otherStamp = NO_IRI;
        public Object otherIri;
    }

    private final NameLookupEntry[] nameLookup;
    private final String[] prefixes;
    // For each prefix id, a stamp that is unique to the value the entry has now: set from a counter
    // whenever the entry is set. A name's cached IRI is valid if its stamp is that of the prefix
    // asked for, which is one compare, for both the prefix id and whether the entry was replaced.
    private final long[] prefixStamps;
    private long lastStamp = NO_PREFIX;

    private int lastPrefixIdReference = 0;
    private int lastNameIdReference = 0;

    private int lastPrefixIdSet = 0;
    private int lastNameIdSet = 0;

    private final Function<String, TIri> iriFactory;

    /**
     * Creates a new NameDecoder.
     *
     * @param prefixTableSize The size of the prefix lookup table.
     * @param nameTableSize The size of the name lookup table.
     * @param iriFactory A function that creates an IRI from a string.
     */
    public NameDecoderImpl(int prefixTableSize, int nameTableSize, Function<String, TIri> iriFactory) {
        this.iriFactory = iriFactory;
        nameLookup = new NameLookupEntry[nameTableSize + 1];
        prefixes = new String[prefixTableSize + 1];
        prefixStamps = new long[prefixTableSize + 1];
        Arrays.fill(prefixStamps, UNSET_PREFIX);
        prefixStamps[0] = NO_PREFIX;

        for (int i = 1; i < nameTableSize + 1; i++) {
            nameLookup[i] = new NameLookupEntry();
        }
    }

    /**
     * Update the name table with a new entry.
     *
     * @param nameEntry name row
     * @throws RdfProtoDeserializationError if the identifier is out of bounds
     */
    @Override
    public void updateNames(RdfNameEntry nameEntry) {
        updateNames(nameEntry.getId(), nameEntry.getValue());
    }

    /**
     * Update the name table with a new entry.
     *
     * @param id 1-based identifier, or 0 for "the previous id + 1"
     * @param value new value of the entry
     * @throws RdfProtoDeserializationError if the identifier is out of bounds
     */
    @Override
    public void updateNames(int id, String value) {
        // Branchless! Equivalent to:
        //   if (id == 0) lastNameIdSet++;
        //   else lastNameIdSet = id;
        // Same code is used in the methods below.
        lastNameIdSet = ((lastNameIdSet + 1) & ((id - 1) >> 31)) + id;
        try {
            NameLookupEntry entry = nameLookup[lastNameIdSet];
            entry.name = value;
            // The cached IRIs were made with the old name
            entry.lastStamp = NO_IRI;
            entry.lastIri = null;
            entry.otherStamp = NO_IRI;
            entry.otherIri = null;
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            throw new RdfProtoDeserializationError(
                "Name entry with ID %d is out of bounds of the name lookup table.".formatted(id)
            );
        }
    }

    /**
     * Update the prefix table with a new entry.
     *
     * @param prefixEntry prefix row
     * @throws RdfProtoDeserializationError if the identifier is out of bounds
     */
    @Override
    public void updatePrefixes(RdfPrefixEntry prefixEntry) {
        updatePrefixes(prefixEntry.getId(), prefixEntry.getValue());
    }

    /**
     * Update the prefix table with a new entry.
     *
     * @param id 1-based identifier, or 0 for "the previous id + 1"
     * @param value new value of the entry
     * @throws RdfProtoDeserializationError if the identifier is out of bounds
     */
    @Override
    public void updatePrefixes(int id, String value) {
        lastPrefixIdSet = ((lastPrefixIdSet + 1) & ((id - 1) >> 31)) + id;
        if (lastPrefixIdSet <= 0 || lastPrefixIdSet >= prefixes.length) {
            throw new RdfProtoDeserializationError(
                "Prefix entry with ID %d is out of bounds of the prefix lookup table.".formatted(id)
            );
        }
        prefixes[lastPrefixIdSet] = value;
        // A new stamp: the IRIs cached with the old value of this entry no longer match
        prefixStamps[lastPrefixIdSet] = ++lastStamp;
    }

    /**
     * Reconstruct an IRI from its prefix and name ids.
     *
     * @param prefixId prefix ID
     * @param nameId name ID
     * @return full IRI combining the prefix and the name
     * @throws RdfProtoDeserializationError if the IRI reference is invalid
     */
    @Override
    public TIri decode(int prefixId, int nameId) {
        // Branchless inference of the 0 identifiers. Equivalent to:
        //   if (nameId == 0) nameId = lastNameIdReference + 1;
        //   if (prefixId == 0) prefixId = lastPrefixIdReference;
        //   else lastPrefixIdReference = prefixId;
        lastNameIdReference = ((lastNameIdReference + 1) & ((nameId - 1) >> 31)) + nameId;
        final int resolvedPrefixId = (lastPrefixIdReference =
            (((prefixId - 1) >> 31) & lastPrefixIdReference) + prefixId);
        return decodeResolved(resolvedPrefixId, lastNameIdReference, prefixId, nameId);
    }

    @Override
    public TIri decodeRaw(int prefixId, int nameId) {
        return decodeResolved(prefixId, nameId, prefixId, nameId);
    }

    /**
     * Shared decoding logic for {@link #decode} and {@link #decodeRaw}. Takes the actual
     * (resolved) lookup identifiers; the original (possibly 0-compressed) identifiers are only
     * used in error messages.
     */
    @SuppressWarnings("unchecked")
    private TIri decodeResolved(int prefixId, int nameId, int originalPrefixId, int originalNameId) {
        final NameLookupEntry nameEntry;
        try {
            nameEntry = nameLookup[nameId];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RdfProtoDeserializationError(
                (
                    "Encountered an invalid name table reference (out of bounds). " + "Name ID: %d, Prefix ID: %d"
                ).formatted(originalNameId, originalPrefixId)
            );
        }
        if (nameEntry == null) {
            // Only possible when nameId = 0 is passed to decodeRaw
            throw new RdfProtoDeserializationError(
                "Encountered an invalid name table reference. Name ID: %d, Prefix ID: %d".formatted(
                    originalNameId,
                    originalPrefixId
                )
            );
        }
        final long stamp;
        try {
            stamp = prefixStamps[prefixId];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RdfProtoDeserializationError(
                (
                    "Encountered an invalid prefix table reference (out of bounds). " + "Prefix ID: %d, Name ID: %d"
                ).formatted(prefixId, originalNameId)
            );
        }
        if (nameEntry.lastStamp == stamp) {
            return (TIri) nameEntry.lastIri;
        }
        return (TIri) decodeMiss(nameEntry, prefixId, stamp, originalPrefixId, originalNameId);
    }

    /** The rest of {@link #decodeResolved}, when the last IRI of the name is not the one asked for. */
    private Object decodeMiss(
        NameLookupEntry nameEntry,
        int prefixId,
        long stamp,
        int originalPrefixId,
        int originalNameId
    ) {
        final Object iri;
        if (nameEntry.otherStamp == stamp) {
            // The IRI with the prefix before the last one
            iri = nameEntry.otherIri;
        } else {
            final String name = nameEntry.name;
            final String prefix = prefixes[prefixId];
            if (name == null || (prefixId != 0 && prefix == null)) {
                throw new RdfProtoDeserializationError(
                    prefixId == 0
                        ? "Encountered an invalid IRI reference. No prefix, Name ID: %d".formatted(originalNameId)
                        : "Encountered an invalid IRI reference. Prefix ID: %d, Name ID: %d".formatted(
                            originalPrefixId,
                            originalNameId
                        )
                );
            }
            iri = iriFactory.apply(prefixId == 0 ? name : prefix.concat(name));
        }
        // The last IRI becomes the other one
        nameEntry.otherStamp = nameEntry.lastStamp;
        nameEntry.otherIri = nameEntry.lastIri;
        nameEntry.lastStamp = stamp;
        nameEntry.lastIri = iri;
        return iri;
    }
}
