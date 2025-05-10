package eu.neverblink.jelly.convert.rdf4j;

import eu.neverblink.jelly.core.EncodedNamespaceDeclaration;
import eu.neverblink.jelly.core.GraphDeclaration;
import eu.neverblink.jelly.core.utils.DatasetAdapter;
import eu.neverblink.jelly.core.utils.GraphAdapter;
import eu.neverblink.jelly.core.utils.IteratorUtils;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

/**
 * Adapters for RDF4J structures.
 * <p>
 * These adapters are used to convert RDF4J structures to triples and quads.
 * <p>
 * Warning: the adapters assume that the underlying structures are static!
 * If they change during iteration, the results are undefined.
 */
public final class Rdf4jAdapters {

    private static final ValueFactory VALUE_FACTORY = SimpleValueFactory.getInstance();

    private Rdf4jAdapters() {}

    /**
     * Converter of RDF4J Models (treated as Graphs) to triples.
     */
    public static final GraphAdapter<Value, Statement, Model> GRAPH_ADAPTER = new GraphAdapter<>() {
        @Override
        public Iterable<Statement> triples(Model statements) {
            return statements::iterator;
        }

        @Override
        public Iterable<EncodedNamespaceDeclaration<Value>> namespaces(Model statements) {
            return () -> {
                final var namespaces = statements.getNamespaces();
                return IteratorUtils.map(namespaces.iterator(), namespace ->
                    new EncodedNamespaceDeclaration<>(
                        namespace.getPrefix(),
                        VALUE_FACTORY.createIRI(namespace.getName())
                    )
                );
            };
        }
    };

    /**
     * Converter of RDF4J Models (treated as Datasets) to quads.
     */
    public static final DatasetAdapter<Value, Statement, Statement, Model> DATASET_ADAPTER = new DatasetAdapter<>() {
        @Override
        public Iterable<Statement> quads(Model dataset) {
            return dataset::iterator;
        }

        @Override
        public Iterable<GraphDeclaration<Value, Statement>> graphs(Model dataset) {
            return () -> {
                final var contexts = dataset.contexts();
                return IteratorUtils.map(contexts.iterator(), context -> {
                    final var statements = dataset.getStatements(null, null, null, context);
                    return new GraphDeclaration<>(context, statements);
                });
            };
        }

        @Override
        public Iterable<EncodedNamespaceDeclaration<Value>> namespaces(Model statements) {
            return GRAPH_ADAPTER.namespaces(statements);
        }
    };
}
