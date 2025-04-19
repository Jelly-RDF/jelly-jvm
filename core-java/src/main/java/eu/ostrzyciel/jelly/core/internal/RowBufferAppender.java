package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfNameEntry;
import eu.ostrzyciel.jelly.core.proto.v1.RdfPrefixEntry;

public interface RowBufferAppender {
    void appendNameEntry(RdfNameEntry nameEntry);
    void appendPrefixEntry(RdfPrefixEntry prefixEntry);
    void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry);
}
