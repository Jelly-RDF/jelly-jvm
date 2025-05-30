package eu.neverblink.jelly.convert.jena;

import eu.neverblink.jelly.core.NamespaceDeclaration;
import eu.neverblink.jelly.core.utils.DatasetAdapter;
import eu.neverblink.jelly.core.utils.GraphAdapter;
import eu.neverblink.jelly.core.utils.GraphHolder;
import eu.neverblink.jelly.core.utils.IteratorUtils;
import java.util.Collections;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.Quad;

/**
 * Adapters for Jena structures.
 * <p>
 * These adapters are used to convert Jena structures to triples and quads.
 * <p>
 * Warning: the adapters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 * <p>
 * These converters are mainly used to feed the streaming encoders in the stream module.
 */
public final class JenaAdapters {

    private JenaAdapters() {}

    /**
     * Converter of Jena Graphs to triples.
     */
    public static final GraphAdapter<Triple, Graph> GRAPH_ADAPTER = new GraphAdapter<>() {
        @Override
        public Iterable<Triple> triples(Graph graph) {
            return graph::find;
        }

        @Override
        public Iterable<NamespaceDeclaration> namespaces(Graph graph) {
            return () -> {
                final var prefixEntries = graph.getPrefixMapping().getNsPrefixMap().entrySet();
                return IteratorUtils.map(prefixEntries.iterator(), entry ->
                    new NamespaceDeclaration(entry.getKey(), entry.getValue())
                );
            };
        }
    };

    /**
     * Converter of Jena Models to triples.
     */
    public static final GraphAdapter<Triple, Model> MODEL_ADAPTER = new GraphAdapter<>() {
        @Override
        public Iterable<Triple> triples(Model model) {
            return GRAPH_ADAPTER.triples(model.getGraph());
        }

        @Override
        public Iterable<NamespaceDeclaration> namespaces(Model model) {
            return GRAPH_ADAPTER.namespaces(model.getGraph());
        }
    };

    /**
     * Converter of Jena DatasetGraphs to quads.
     */
    public static final DatasetAdapter<Node, Triple, Quad, DatasetGraph> DATASET_GRAPH_ADAPTER =
        new DatasetAdapter<>() {
            @Override
            public Iterable<Quad> quads(DatasetGraph dataset) {
                return dataset::find;
            }

            @Override
            public Iterable<GraphHolder<Node, Triple>> graphs(DatasetGraph dataset) {
                return () -> {
                    final var defaultGraph = dataset.getDefaultGraph();
                    final var defaultGraphTriples = defaultGraph.isEmpty()
                        ? Collections.<GraphHolder<Node, Triple>>emptyIterator()
                        : IteratorUtils.of(new GraphHolder<>(Quad.defaultGraphIRI, defaultGraph::find));

                    final var graphNodes = dataset.listGraphNodes();
                    final var graphEntries = IteratorUtils.map(graphNodes, graphNode ->
                        new GraphHolder<>(graphNode, () -> dataset.getGraph(graphNode).find())
                    );

                    return IteratorUtils.concat(defaultGraphTriples, graphEntries);
                };
            }

            @Override
            public Iterable<NamespaceDeclaration> namespaces(DatasetGraph dataset) {
                return () -> {
                    final var prefixEntries = dataset.getDefaultGraph().getPrefixMapping().getNsPrefixMap().entrySet();
                    return IteratorUtils.map(prefixEntries.iterator(), entry ->
                        new NamespaceDeclaration(entry.getKey(), entry.getValue())
                    );
                };
            }
        };

    /**
     * Converter of Jena Datasets to quads.
     */
    public static final DatasetAdapter<Node, Triple, Quad, Dataset> DATASET_ADAPTER = new DatasetAdapter<>() {
        @Override
        public Iterable<Quad> quads(Dataset dataset) {
            return DATASET_GRAPH_ADAPTER.quads(dataset.asDatasetGraph());
        }

        @Override
        public Iterable<GraphHolder<Node, Triple>> graphs(Dataset dataset) {
            return DATASET_GRAPH_ADAPTER.graphs(dataset.asDatasetGraph());
        }

        @Override
        public Iterable<NamespaceDeclaration> namespaces(Dataset dataset) {
            return DATASET_GRAPH_ADAPTER.namespaces(dataset.asDatasetGraph());
        }
    };
}
