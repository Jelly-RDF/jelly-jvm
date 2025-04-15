package eu.ostrzyciel.jelly.core.internal;

import eu.ostrzyciel.jelly.core.proto.v1.Rdf;

public interface RowBufferAppender {
    void appendNameEntry(Rdf.RdfNameEntry nameEntry);
    void appendPrefixEntry(Rdf.RdfPrefixEntry prefixEntry);
    void appendDatatypeEntry(Rdf.RdfDatatypeEntry datatypeEntry);
}
