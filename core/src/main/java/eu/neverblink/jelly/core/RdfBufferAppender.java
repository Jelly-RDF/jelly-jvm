package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.proto.v1.*;

/**
 * Interface for appending lookup entries to the row buffer and RDF terms to statements.
 * <p>
 * This is used by NodeEncoder.
 */
public interface RdfBufferAppender<TNode> {
    //    byte TERM_IRI = 1;
    //    byte TERM_BNODE = 2;
    //    byte TERM_LITERAL = 3;
    //    byte TERM_TRIPLE = 4;
    //    byte TERM_DEFAULT_GRAPH = 5;

    // Lookup entries
    void appendNameEntry(RdfNameEntry nameEntry);
    void appendPrefixEntry(RdfPrefixEntry prefixEntry);
    void appendDatatypeEntry(RdfDatatypeEntry datatypeEntry);

    //    // RDF terms
    //    void appendIri(RdfIri iri);
    //    void appendBlankNode(String label);
    //    void appendLiteral(RdfLiteral literal);
    RdfTriple appendQuotedTriple(TNode subject, TNode predicate, TNode object);
    //    void appendDefaultGraph();
}
