package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;

/**
 * Interface for NameDecoder exposed for Jelly extensions.
 * @param <TIri> type of the IRI
 */
public interface NameDecoder<TIri> {
    /**
     * Update the name table with a new entry.
     * @param nameEntry new name entry
     */
    void updateNames(RdfNameEntry nameEntry);

    /**
     * Update the prefix table with a new entry.
     * @param prefixEntry new prefix entry
     */
    void updatePrefixes(RdfPrefixEntry prefixEntry);

    /**
     * Reconstruct an IRI from its prefix and name ids.
     * @param prefixId prefix id of IRI row from the Jelly proto
     * @param nameId name id of IRI row from the Jelly proto
     * @return full IRI combining the prefix and the name
     */
    TIri decode(int prefixId, int nameId);

    /**
     * Reconstruct an IRI from its prefix and name ids, WITHOUT applying the same-prefix /
     * next-name inference (the prefix_id = 0 / name_id = 0 compression). The ids passed here are
     * the actual lookup table identifiers: prefixId = 0 means "no prefix" and nameId must
     * be >= 1.
     * <p>
     * This is used by extensions that define their own ordering of the IRI inference state
     * (e.g., the columnar Jelly-SPARQL encoding) and resolve the inference themselves.
     *
     * @param prefixId actual prefix id, or 0 for no prefix
     * @param nameId actual name id (1-based)
     * @return full IRI combining the prefix and the name
     */
    TIri decodeRaw(int prefixId, int nameId);
}
