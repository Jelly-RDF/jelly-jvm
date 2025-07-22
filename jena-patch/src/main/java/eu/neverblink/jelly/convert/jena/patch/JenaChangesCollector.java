package eu.neverblink.jelly.convert.jena.patch;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType;
import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFChanges;
import org.apache.jena.sparql.core.Quad;

/**
 * A collector for Jena RDFChanges operations that can be replayed later.
 * <p>
 * This class collects changes in a list and allows them to be replayed to a destination RDFChanges instance.
 * It supports both triples and quads based on the specified PatchStatementType.
 */
@ExperimentalApi
public final class JenaChangesCollector implements RDFChanges {

    private final List<JenaChangesItem> items = new ArrayList<>();
    private final PatchStatementType stType;

    /**
     * Creates a new JenaChangesCollector with the specified statement type.
     *
     * @param stType How to interpret the statements: TRIPLES or QUADS.
     */
    JenaChangesCollector(PatchStatementType stType) {
        this.stType = stType;
    }

    /**
     * Returns the number of changes collected.
     * @return The size of the collected changes.
     */
    public List<JenaChangesItem> getChanges() {
        return items;
    }

    /**
     * Returns the number of changes collected.
     * @return The size of the collected changes.
     */
    public int size() {
        return items.size();
    }

    /**
     * Replays the collected changes to the specified destination RDFChanges instance.
     * @param destination The RDFChanges instance to which the changes will be applied.
     * @param callStartFinish If true, calls start() and finish() on the destination.
     */
    public void replay(RDFChanges destination, boolean callStartFinish) {
        if (callStartFinish) destination.start();
        for (JenaChangesItem item : items) {
            item.applyTo(destination);
        }
        if (callStartFinish) destination.finish();
    }

    @Override
    public void header(String field, Node value) {
        items.add(new Header(field, value));
    }

    @Override
    public void add(Node g, Node s, Node p, Node o) {
        items.add(new Add(coerceGraph(g), s, p, o));
    }

    @Override
    public void delete(Node g, Node s, Node p, Node o) {
        items.add(new Delete(coerceGraph(g), s, p, o));
    }

    @Override
    public void addPrefix(Node gn, String prefix, String uriStr) {
        items.add(new AddPrefix(coerceGraph(gn), prefix, uriStr));
    }

    @Override
    public void deletePrefix(Node gn, String prefix) {
        items.add(new DeletePrefix(coerceGraph(gn), prefix));
    }

    @Override
    public void txnBegin() {
        items.add(TxnBegin.INSTANCE);
    }

    @Override
    public void txnCommit() {
        items.add(TxnCommit.INSTANCE);
    }

    @Override
    public void txnAbort() {
        items.add(TxnAbort.INSTANCE);
    }

    @Override
    public void segment() {
        items.add(Segment.INSTANCE);
    }

    @Override
    public void start() {}

    @Override
    public void finish() {}

    private Node coerceGraph(Node g) {
        if (g == null && stType == PatchStatementType.QUADS) {
            return Quad.defaultGraphNodeGenerated;
        } else if (stType == PatchStatementType.TRIPLES) {
            return null;
        } else {
            return g;
        }
    }

    /// Inner classes for JenaChangesItem

    public sealed interface JenaChangesItem {
        void applyTo(RDFChanges destination);
    }

    private record Header(String field, Node value) implements JenaChangesItem {
        @Override
        public void applyTo(RDFChanges destination) {
            destination.header(field, value);
        }
    }

    private record Add(Node g, Node s, Node p, Node o) implements JenaChangesItem {
        @Override
        public void applyTo(RDFChanges destination) {
            destination.add(g, s, p, o);
        }
    }

    private record Delete(Node g, Node s, Node p, Node o) implements JenaChangesItem {
        @Override
        public void applyTo(RDFChanges destination) {
            destination.delete(g, s, p, o);
        }
    }

    private record AddPrefix(Node gn, String prefix, String uriStr) implements JenaChangesItem {
        @Override
        public void applyTo(RDFChanges destination) {
            destination.addPrefix(gn, prefix, uriStr);
        }
    }

    private record DeletePrefix(Node gn, String prefix) implements JenaChangesItem {
        @Override
        public void applyTo(RDFChanges destination) {
            destination.deletePrefix(gn, prefix);
        }
    }

    private record TxnBegin() implements JenaChangesItem {
        public static final TxnBegin INSTANCE = new TxnBegin();

        @Override
        public void applyTo(RDFChanges destination) {
            destination.txnBegin();
        }
    }

    private record TxnCommit() implements JenaChangesItem {
        public static final TxnCommit INSTANCE = new TxnCommit();

        @Override
        public void applyTo(RDFChanges destination) {
            destination.txnCommit();
        }
    }

    private record TxnAbort() implements JenaChangesItem {
        public static final TxnAbort INSTANCE = new TxnAbort();

        @Override
        public void applyTo(RDFChanges destination) {
            destination.txnAbort();
        }
    }

    private record Segment() implements JenaChangesItem {
        public static final Segment INSTANCE = new Segment();

        @Override
        public void applyTo(RDFChanges destination) {
            destination.segment();
        }
    }
}
