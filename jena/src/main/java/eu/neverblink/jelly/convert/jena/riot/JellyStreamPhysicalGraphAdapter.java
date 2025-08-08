package eu.neverblink.jelly.convert.jena.riot;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;

final class JellyStreamPhysicalGraphAdapter implements StreamRDF {

    private final JellyStreamWriter writerDelegate;
    private Node currentGraph = null;
    private boolean started = false;

    public JellyStreamPhysicalGraphAdapter(JellyStreamWriter writerDelegate) {
        this.writerDelegate = writerDelegate;
    }

    @Override
    public void start() {
        writerDelegate.start();
    }

    @Override
    public void triple(Triple triple) {
        if (!started) {
            writerDelegate.graphStart(null);
            started = true;
        }
        if (currentGraph == null) {
            writerDelegate.triple(triple);
            return;
        }
        writerDelegate.graphEnd();
        currentGraph = null;
        writerDelegate.graphStart(null); // null -> default graph
        writerDelegate.triple(triple);
    }

    @Override
    public void quad(Quad quad) {
        if (!started) {
            writerDelegate.graphStart(quad.getGraph());
            started = true;
        }
        if (currentGraph == null && quad.isDefaultGraph()) {
            writerDelegate.triple(quad.asTriple());
            return;
        }
        if (currentGraph != null && currentGraph.equals(quad.getGraph())) {
            writerDelegate.triple(quad.asTriple());
            return;
        }
        writerDelegate.graphEnd();
        currentGraph = quad.getGraph();
        writerDelegate.graphStart(currentGraph);
        writerDelegate.triple(quad.asTriple());
    }

    @Override
    public void base(String base) {
        writerDelegate.base(base);
    }

    @Override
    public void prefix(String prefix, String iri) {
        writerDelegate.prefix(prefix, iri);
    }

    @Override
    public void finish() {
        writerDelegate.graphEnd();
        writerDelegate.finish();
    }
}
