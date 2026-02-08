package org.apache.jena.graph;

import eu.neverblink.jelly.core.InternalApi;

/**
 * Hack to avoid very complex dependency & method binding workarounds needed to support
 * Jena 5.3, 5.4, and 6.0 at the same time.
 * This class should be removed as soon as Jena 5.3 support is dropped.
 * See: <a href="https://github.com/Jelly-RDF/jelly-jvm/issues/622">issue</a>
 */
@InternalApi
public final class JenaCompatHelper {

    public static boolean isNodeTriple(Node node) {
        return node instanceof Node_Triple;
    }

    public static Node createTripleNode(Node s, Node p, Node o) {
        return new Node_Triple(s, p, o);
    }

    public static Node createTripleNode(Triple t) {
        return new Node_Triple(t);
    }
}
