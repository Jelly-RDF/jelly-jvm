package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Receives the new lookup entries that the encoding of RDF terms needs.
 */
public interface RdfBufferAppender {
    void appendNameEntry(RdfNameEntry nameEntry);
    void appendPrefixEntry(RdfPrefixEntry prefixEntry);
    void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry);
}
