package eu.neverblink.jelly.convert.jena;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

/**
 * Helper class to abstract away differences between Jena 5 and Jena 6 regarding
 * triple term / triple node handling.
 * <p>
 * Call getInstance() to get the singleton instance of this class, which will be either a
 * Jena5CompatHelper or a Jena6CompatHelper depending on the Jena version detected at runtime.
 * <p>
 * Remove this when we drop support for Jena 5:
 * <a href="https://github.com/Jelly-RDF/jelly-jvm/issues/622">issue</a>
 */
abstract class JenaCompatHelper {

    public abstract boolean isNodeTriple(Node node);

    public abstract Node createTripleNode(Node s, Node p, Node o);

    public abstract boolean isJena54OrLater();

    private static final JenaCompatHelper instance;

    public static JenaCompatHelper getInstance() {
        return instance;
    }

    static {
        final var testNode = NodeFactory.createBlankNode();
        boolean jena54orLater;
        try {
            testNode.isTripleTerm();
            jena54orLater = true;
        } catch (NoSuchMethodError e) {
            jena54orLater = false;
        }
        if (jena54orLater) {
            instance = new Jena6CompatHelper();
        } else {
            instance = new Jena5CompatHelper();
        }
    }

    private static final class Jena5CompatHelper extends JenaCompatHelper {

        @Override
        public boolean isJena54OrLater() {
            return false;
        }

        // Jena deprecated .isNodeTriple() in favor of .isTripleTerm() in version 5.4.
        // To maintain compatibility with 5.0.x–5.3.x. we must continue using .isNodeTriple().
        @SuppressWarnings("removal")
        @Override
        public boolean isNodeTriple(Node node) {
            return node.isNodeTriple();
        }

        @Override
        public Node createTripleNode(Node s, Node p, Node o) {
            return NodeFactory.createTripleNode(s, p, o);
        }
    }

    private static final class Jena6CompatHelper extends JenaCompatHelper {

        @Override
        public boolean isJena54OrLater() {
            return true;
        }

        @Override
        public boolean isNodeTriple(Node node) {
            return node.isTripleTerm();
        }

        @Override
        public Node createTripleNode(Node s, Node p, Node o) {
            return NodeFactory.createTripleTerm(s, p, o);
        }
    }
}
