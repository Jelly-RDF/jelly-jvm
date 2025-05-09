package eu.neverblink.jelly.core;

/**
 * Simple holder for namespace declarations.
 * <p>
 * It is different from {@link NamespaceDeclaration} in that it holds iri as a node type from the RDF libraries.
 * This isn't actually needed for the core functionality, but it's useful if you want to pass namespace declarations
 * around in a type-safe way. It's used for example in the stream module.
 *
 * @param <TNode> type of RDF nodes in the library
 * @param prefix short name of the namespace (e.g., "rdf"), without a colon
 * @param iri namespace IRI, encoded as TNode (e.g., "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
 */
public record EncodedNamespaceDeclaration<TNode>(String prefix, TNode iri) {}
