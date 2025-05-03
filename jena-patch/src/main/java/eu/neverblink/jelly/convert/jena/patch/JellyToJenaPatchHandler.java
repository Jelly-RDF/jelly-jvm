package eu.neverblink.jelly.convert.jena.patch;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.patch.PatchHandler;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFChanges;

/**
 * Patch handler to convert Jelly-Patch operations to Jena RDFChanges operations.
 * <p>
 * This class provides a handler in Jelly terms that relays all operations to a Jena RDFChanges
 */
@ExperimentalApi
final class JellyToJenaPatchHandler implements PatchHandler.AnyPatchHandler<Node> {

    private final RDFChanges jenaStream;

    JellyToJenaPatchHandler(RDFChanges jenaStream) {
        this.jenaStream = jenaStream;
    }

    @Override
    public void addQuad(Node subject, Node predicate, Node object, Node graph) {
        jenaStream.add(graph, subject, predicate, object);
    }

    @Override
    public void deleteQuad(Node subject, Node predicate, Node object, Node graph) {
        jenaStream.delete(graph, subject, predicate, object);
    }

    @Override
    public void addTriple(Node subject, Node predicate, Node object) {
        jenaStream.add(null, subject, predicate, object);
    }

    @Override
    public void deleteTriple(Node subject, Node predicate, Node object) {
        jenaStream.delete(null, subject, predicate, object);
    }

    @Override
    public void transactionStart() {
        jenaStream.txnBegin();
    }

    @Override
    public void transactionCommit() {
        jenaStream.txnCommit();
    }

    @Override
    public void transactionAbort() {
        jenaStream.txnAbort();
    }

    @Override
    public void addNamespace(String name, Node iriValue, Node graph) {
        jenaStream.addPrefix(graph, name, iriValue.getURI());
    }

    @Override
    public void deleteNamespace(String name, Node iriValue, Node graph) {
        jenaStream.deletePrefix(graph, name);
    }

    @Override
    public void header(String key, Node value) {
        jenaStream.header(key, value);
    }

    @Override
    public void punctuation() {
        jenaStream.segment();
    }
}
