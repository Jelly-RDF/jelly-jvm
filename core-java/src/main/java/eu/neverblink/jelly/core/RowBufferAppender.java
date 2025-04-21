package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfNameEntry;
import eu.neverblink.jelly.core.proto.v1.RdfPrefixEntry;

/**
 * Interface for appending lookup entries to the row buffer.
 * <p>
 * This is used by NodeEncoder.
 */
public interface RowBufferAppender {
    void appendNameEntry(RdfNameEntry nameEntry);
    void appendPrefixEntry(RdfPrefixEntry prefixEntry);
    void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry);
}
