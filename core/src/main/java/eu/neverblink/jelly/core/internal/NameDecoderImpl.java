package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.NameDecoder;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;
import java.util.function.Function;

/**
 * Class for decoding RDF IRIs from their Jelly representation.
 * @param <TIri> The type of the IRI in the target RDF library.
 */
@InternalApi
public final class NameDecoderImpl<TIri> implements NameDecoder<TIri> {

    private static final class NameLookupEntry {

        // Primary: the actual name
        public String name;
        // Secondary values (may be mutated without invalidating the primary value)
        // Reference to the last prefix ID used to encode the IRI with this name
        public int lastPrefixId;
        // Serial number of the last prefix ID used to encode the IRI with this name
        public int lastPrefixSerial;
        // Last IRI encoded with this name
        public Object lastIri;
    }

    private static final class PrefixLookupEntry {

        public String prefix;
        public int serial = -1;
    }

    private final NameLookupEntry[] nameLookup;
    private final PrefixLookupEntry[] prefixLookup;

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
        prefixLookup = new PrefixLookupEntry[prefixTableSize + 1];

        for (int i = 1; i < nameTableSize + 1; i++) {
            nameLookup[i] = new NameLookupEntry();
        }
        for (int i = 1; i < prefixTableSize + 1; i++) {
            prefixLookup[i] = new PrefixLookupEntry();
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
            // Enough to invalidate the last IRI – we don't have to touch the serial number.
            entry.lastPrefixId = 0;
            // Set to null is required to avoid a false positive in the decode method for cases without a prefix.
            entry.lastIri = null;
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
        try {
            PrefixLookupEntry entry = prefixLookup[lastPrefixIdSet];
            entry.prefix = value;
            entry.serial++;
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            throw new RdfProtoDeserializationError(
                "Prefix entry with ID %d is out of bounds of the prefix lookup table.".formatted(id)
            );
        }
    }

    /**
     * Reconstruct an IRI from its prefix and name ids.
     *
     * @param prefixId prefix ID
     * @param nameId name ID
     * @return full IRI combining the prefix and the name
     * @throws RdfProtoDeserializationError if the IRI reference is invalid
     * @throws NullPointerException if the IRI reference is invalid
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
        NameLookupEntry nameEntry;
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

        if (prefixId != 0) {
            // Name and prefix
            PrefixLookupEntry prefixEntry;
            try {
                prefixEntry = prefixLookup[prefixId];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new RdfProtoDeserializationError(
                    (
                        "Encountered an invalid prefix table reference (out of bounds). " + "Prefix ID: %d, Name ID: %d"
                    ).formatted(prefixId, originalNameId)
                );
            }
            if (nameEntry.lastPrefixId != prefixId || nameEntry.lastPrefixSerial != prefixEntry.serial) {
                // Update the last prefix
                nameEntry.lastPrefixId = prefixId;
                nameEntry.lastPrefixSerial = prefixEntry.serial;
                // And compute a new IRI
                nameEntry.lastIri = iriFactory.apply(prefixEntry.prefix.concat(nameEntry.name));
            } else if (nameEntry.lastIri == null) {
                throw new RdfProtoDeserializationError(
                    "Encountered an invalid IRI reference. Prefix ID: %d, Name ID: %d".formatted(
                        originalPrefixId,
                        originalNameId
                    )
                );
            }
        } else if (nameEntry.lastPrefixId != 0 || nameEntry.lastIri == null) {
            // No prefix. The cached IRI (if any) may have been computed with a prefix – recompute.
            if (nameEntry.name == null) {
                throw new RdfProtoDeserializationError(
                    "Encountered an invalid IRI reference. No prefix, Name ID: %d".formatted(originalNameId)
                );
            }
            nameEntry.lastPrefixId = 0;
            nameEntry.lastIri = iriFactory.apply(nameEntry.name);
        }

        return (TIri) nameEntry.lastIri;
    }
}
