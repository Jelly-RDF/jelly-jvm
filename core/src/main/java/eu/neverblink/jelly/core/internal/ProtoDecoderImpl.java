package eu.neverblink.jelly.core.internal;

import static eu.neverblink.jelly.core.JellyOptions.*;
import static eu.neverblink.jelly.core.internal.BaseJellyOptions.*;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfColumn;
import eu.neverblink.jelly.core.proto.v1.RdfColumnBatch;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfGraphStart;
import eu.neverblink.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.jelly.core.proto.v1.RdfStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;
import eu.neverblink.protoc.java.runtime.RepeatedInt;

/**
 * Base class for stateful decoders of protobuf RDF streams.
 *
 * @param <TNode>     the type of the node
 * @param <TDatatype> the type of the datatype
 * @see ProtoDecoder the base (extendable) interface.
 * @see DecoderBase for common methods shared by all decoders.
 */
@InternalApi
public abstract sealed class ProtoDecoderImpl<TNode, TDatatype> extends ProtoDecoder<TNode, TDatatype> {

    protected final RdfHandler<TNode> protoHandler;
    protected final RdfStreamOptions supportedOptions;

    private RdfStreamOptions currentOptions = null;

    // Column layout (Jelly-RDF 1.2) state.
    // Statements of a batch are decoded in chunks of this many rows, so that the memory needed
    // does not depend on the row count declared by the batch.
    private static final int CHUNK_ROWS = 1024;
    private ColumnDecoder<TNode, TDatatype> columnDecoder = null;
    private Object[] chunkSubjects = null;
    private Object[] chunkPredicates = null;
    private Object[] chunkObjects = null;
    private Object[] chunkGraphs = null;
    private TNode defaultGraphNode = null;
    // Rows of the current RDF message that were not assigned to a finished message yet
    private long rowsInCurrentMessage = 0;

    public ProtoDecoderImpl(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        RdfHandler<TNode> protoHandler,
        RdfStreamOptions supportedOptions
    ) {
        super(converter);
        this.protoHandler = protoHandler;
        this.supportedOptions = supportedOptions;
    }

    /**
     * Returns the size of the name table.
     *
     * @return the size of the name table if options are set, otherwise the default size
     */
    @Override
    protected int getNameTableSize() {
        if (currentOptions == null) {
            return SMALL_NAME_TABLE_SIZE;
        }

        return currentOptions.getMaxNameTableSize();
    }

    /**
     * Returns the size of the prefix table.
     *
     * @return the size of the prefix table if options are set, otherwise the default size
     */
    @Override
    protected int getPrefixTableSize() {
        if (currentOptions == null) {
            return SMALL_PREFIX_TABLE_SIZE;
        }

        return currentOptions.getMaxPrefixTableSize();
    }

    /**
     * Returns the size of the datatype table.
     *
     * @return the size of the datatype table if options are set, otherwise the default size
     */
    @Override
    protected int getDatatypeTableSize() {
        if (currentOptions == null) {
            return SMALL_DT_TABLE_SIZE;
        }

        return currentOptions.getMaxDatatypeTableSize();
    }

    /**
     * Returns the received stream options from the producer.
     *
     * @return the stream options if set, otherwise null
     */
    @Override
    public RdfStreamOptions getStreamOptions() {
        return currentOptions;
    }

    private void setStreamOptions(RdfStreamOptions options) {
        if (currentOptions == null) {
            this.currentOptions = options;
            return;
        }
        if (JellyConstants.isRowLayout(currentOptions.getVersion())) {
            // Row layout: the options must be the same as at the start, and do not change anything
            return;
        }
        // Column layout: restated options, as in a concatenated stream
        if (
            options.getVersion() != currentOptions.getVersion() ||
            options.getPhysicalTypeValue() != currentOptions.getPhysicalTypeValue() ||
            options.getStreamTypeValue() != currentOptions.getStreamTypeValue()
        ) {
            throw new RdfProtoDeserializationError(
                "The protocol version, physical stream type, and stream type must be the same in all stream options of a stream."
            );
        }
        // In a stream of RDF Messages, the current message ends here, as at the end of the stream
        if (rowsInCurrentMessage > 0 && currentOptions.getStreamTypeValue() == RdfStreamType.MESSAGES_VALUE) {
            rowsInCurrentMessage = 0;
            protoHandler.handleMessageEnd();
        }
        this.currentOptions = options;
        // Recreated on next use, with the new table sizes
        resetLookups();
        if (columnDecoder != null) {
            columnDecoder.setRdfVersions(options.getRdfVersionValue(), supportedOptions.getRdfVersionValue());
        }
    }

    /** Whether the stream uses the column layout (Jelly-RDF 1.2). Only valid once the options are known. */
    private boolean isColumnLayout() {
        return currentOptions != null && !JellyConstants.isRowLayout(currentOptions.getVersion());
    }

    /**
     * Internal implementation of ingestRow that does not allow overriding.
     * @param row the row to ingest
     */
    protected final void ingestRowInternal(RdfStreamRow row) {
        if (row == null) {
            throw new RdfProtoDeserializationError("Row kind is not set.");
        }

        final int kind = row.getRowFieldNumber();
        if (kind != RdfStreamRow.OPTIONS && isColumnLayout()) {
            throw new RdfProtoDeserializationError(
                "Jelly-RDF 1.2 streams (column layout) may only have the stream options in their rows."
            );
        }
        switch (kind) {
            case RdfStreamRow.OPTIONS -> handleOptions(row.getOptions());
            case RdfStreamRow.NAME -> getNameDecoder().updateNames(row.getName());
            case RdfStreamRow.PREFIX -> getNameDecoder().updatePrefixes(row.getPrefix());
            case RdfStreamRow.DATATYPE -> handleDatatype(row.getDatatype());
            case RdfStreamRow.NAMESPACE -> handleNamespace(row.getNamespace());
            case RdfStreamRow.TRIPLE -> handleTriple(row.getTriple());
            case RdfStreamRow.QUAD -> handleQuad(row.getQuad());
            case RdfStreamRow.GRAPH_START -> handleGraphStart(row.getGraphStart());
            case RdfStreamRow.GRAPH_END -> handleGraphEnd();
            default -> throw new RdfProtoDeserializationError("Row kind is not set or unknown.");
        }
    }

    protected void handleOptions(RdfStreamOptions options) {
        checkCompatibility(options, supportedOptions);
        setStreamOptions(options);
    }

    protected void handleDatatype(RdfDatatypeEntry datatype) {
        final TDatatype dt;
        try {
            dt = converter.makeDatatype(datatype.getValue());
        } catch (RdfProtoDeserializationError e) {
            throw e;
        } catch (Exception e) {
            // Most likely the IRI was not accepted by the RDF library
            throw new RdfProtoDeserializationError(
                "Error while decoding datatype '%s': %s".formatted(datatype.getValue(), e),
                e
            );
        }
        getDatatypeLookup().update(datatype.getId(), dt);
    }

    protected void handleNamespace(RdfNamespaceDeclaration namespace) {
        final var iri = namespace.getValue();
        if (iri == null) {
            throw new RdfProtoDeserializationError(
                "Namespace declaration '%s' has no IRI.".formatted(namespace.getName())
            );
        }
        final TNode node;
        try {
            node = getNameDecoder().decode(iri.getPrefixId(), iri.getNameId());
        } catch (RdfProtoDeserializationError e) {
            throw e;
        } catch (Exception e) {
            // Same as above
            throw new RdfProtoDeserializationError(
                "Error while decoding the IRI of namespace declaration '%s': %s".formatted(namespace.getName(), e),
                e
            );
        }
        protoHandler.handleNamespace(namespace.getName(), node);
    }

    /**
     * Internal implementation of ingestColumns that does not allow overriding.
     * @param batch the column batch to ingest
     */
    protected final void ingestColumnsInternal(RdfColumnBatch batch) {
        if (currentOptions == null) {
            throw new RdfProtoDeserializationError("Stream options were not received before the first frame content.");
        }
        if (!isColumnLayout()) {
            throw new RdfProtoDeserializationError(
                "Jelly-RDF 1.0 and 1.1 streams (row layout) cannot have column batches."
            );
        }
        if (columnDecoder == null) {
            columnDecoder = new ColumnDecoder<>(this);
            columnDecoder.setRdfVersions(currentOptions.getRdfVersionValue(), supportedOptions.getRdfVersionValue());
        }
        final int rows = batch.getRowCount();
        if (rows < 0 || rows > ColumnLayout.MAX_ROWS) {
            throw new RdfProtoDeserializationError(
                "Invalid row count %s: a batch may have at most %d rows.".formatted(
                    Integer.toUnsignedString(rows),
                    ColumnLayout.MAX_ROWS
                )
            );
        }
        final RepeatedInt messageLengths = batch.getMessageLengths();
        checkMessageLengths(messageLengths, rows);

        columnDecoder.applyLookupEntries(batch.getNames(), batch.getPrefixes(), batch.getDatatypes());
        if (!batch.getNamespaces().isEmpty()) {
            handleColumnNamespaces(batch);
        }

        final boolean quads = currentOptions.getPhysicalType() == PhysicalStreamType.QUADS;
        final RdfColumn graphColumn = batch.getGraphs();
        if (!quads && graphColumn != null) {
            throw new RdfProtoDeserializationError("A TRIPLES stream cannot have a graph column.");
        }
        int nextBoundary = 0;
        if (rows > 0) {
            final ColumnDecoder<TNode, TDatatype>.Cursor subjects = cursor(batch.getSubjects(), rows, "subject");
            final ColumnDecoder<TNode, TDatatype>.Cursor predicates = cursor(batch.getPredicates(), rows, "predicate");
            final ColumnDecoder<TNode, TDatatype>.Cursor objects = cursor(batch.getObjects(), rows, "object");
            final ColumnDecoder<TNode, TDatatype>.Cursor graphs =
                graphColumn == null ? null : cursor(graphColumn, rows, "graph");
            final int chunk = Math.min(rows, CHUNK_ROWS);
            if (chunkSubjects == null || chunkSubjects.length < chunk) {
                chunkSubjects = new Object[chunk];
                chunkPredicates = new Object[chunk];
                chunkObjects = new Object[chunk];
                chunkGraphs = new Object[chunk];
            }
            // The position of the next message boundary, in rows of this batch
            long boundaryAt = nextBoundary < messageLengths.size() ? messageLengths.get(0) : -1;
            for (int start = 0; start < rows; start += CHUNK_ROWS) {
                final int n = Math.min(CHUNK_ROWS, rows - start);
                fillChunk(subjects, chunkSubjects, n, "subject");
                fillChunk(predicates, chunkPredicates, n, "predicate");
                fillChunk(objects, chunkObjects, n, "object");
                if (graphs != null) {
                    fillChunk(graphs, chunkGraphs, n, "graph");
                }
                for (int i = 0; i < n; i++) {
                    while (boundaryAt == start + i) {
                        endMessage();
                        nextBoundary++;
                        boundaryAt =
                            nextBoundary < messageLengths.size() ? boundaryAt + messageLengths.get(nextBoundary) : -1;
                    }
                    rowsInCurrentMessage++;
                    handleColumnRow(
                        chunkSubjects[i],
                        chunkPredicates[i],
                        chunkObjects[i],
                        graphs == null ? null : chunkGraphs[i]
                    );
                }
            }
            finishColumn(subjects, "subject");
            finishColumn(predicates, "predicate");
            finishColumn(objects, "object");
            if (graphs != null) {
                finishColumn(graphs, "graph");
            }
        }
        // Boundaries after the last row of the batch
        while (nextBoundary < messageLengths.size()) {
            endMessage();
            nextBoundary++;
        }
    }

    private void checkMessageLengths(RepeatedInt messageLengths, int rows) {
        if (messageLengths.isEmpty()) {
            return;
        }
        if (currentOptions.getStreamTypeValue() != RdfStreamType.MESSAGES_VALUE) {
            throw new RdfProtoDeserializationError("Message boundaries are only valid in streams of type MESSAGES.");
        }
        long sum = 0;
        for (int i = 0; i < messageLengths.size(); i++) {
            sum += Integer.toUnsignedLong(messageLengths.get(i));
        }
        if (sum > rows) {
            throw new RdfProtoDeserializationError(
                "The message lengths add up to %d, more than the %d rows of the batch.".formatted(sum, rows)
            );
        }
    }

    private void endMessage() {
        rowsInCurrentMessage = 0;
        protoHandler.handleMessageEnd();
    }

    private void handleColumnNamespaces(RdfColumnBatch batch) {
        // The IRIs of the declarations of a batch have their own inference state
        final ColumnDecoder<TNode, TDatatype>.IriState iris = columnDecoder.newIriState();
        for (final RdfNamespaceDeclaration namespace : batch.getNamespaces()) {
            final var iri = namespace.getValue();
            if (iri == null) {
                throw new RdfProtoDeserializationError(
                    "Namespace declaration '%s' has no IRI.".formatted(namespace.getName())
                );
            }
            final TNode node;
            try {
                node = iris.decode(iri);
            } catch (RdfProtoDeserializationError e) {
                throw e;
            } catch (Exception e) {
                throw new RdfProtoDeserializationError(
                    "Error while decoding the IRI of namespace declaration '%s': %s".formatted(namespace.getName(), e),
                    e
                );
            }
            protoHandler.handleNamespace(namespace.getName(), node);
        }
    }

    private ColumnDecoder<TNode, TDatatype>.Cursor cursor(RdfColumn column, int rows, String position) {
        if (column == null) {
            throw new RdfProtoDeserializationError(
                "The batch has %d rows, but no %s column.".formatted(rows, position)
            );
        }
        // Only the object may be a literal or a triple term, and only the predicate must be an IRI
        final boolean object = position.equals("object");
        if (!object && (!column.getLexValues().isEmpty() || !column.getTripleTerms().isEmpty())) {
            throw new RdfProtoDeserializationError(
                "The %s column may only have IRIs and blank nodes.".formatted(position)
            );
        }
        if (position.equals("predicate") && !column.getBnodes().isEmpty()) {
            throw new RdfProtoDeserializationError("The predicate column may only have IRIs.");
        }
        try {
            return columnDecoder.cursor(column, rows, position.equals("graph"), position);
        } catch (RdfProtoDeserializationError e) {
            throw e;
        } catch (Exception e) {
            throw columnError(position, e);
        }
    }

    private void fillChunk(ColumnDecoder<TNode, TDatatype>.Cursor cursor, Object[] out, int n, String position) {
        try {
            cursor.fill(out, 0, n);
        } catch (RdfProtoDeserializationError e) {
            throw e;
        } catch (Exception e) {
            throw columnError(position, e);
        }
    }

    private static void finishColumn(ColumnDecoder<?, ?>.Cursor cursor, String position) {
        try {
            cursor.finish();
        } catch (RdfProtoDeserializationError e) {
            throw new RdfProtoDeserializationError("Error in the %s column: %s".formatted(position, e.getMessage()), e);
        }
    }

    private static RdfProtoDeserializationError columnError(String position, Exception e) {
        return new RdfProtoDeserializationError("Error while decoding the %s column: %s".formatted(position, e), e);
    }

    /**
     * The default graph node of the RDF library, created once.
     */
    protected final TNode defaultGraphNode() {
        if (defaultGraphNode == null) {
            defaultGraphNode = converter.makeDefaultGraphNode();
        }
        return defaultGraphNode;
    }

    /**
     * Handles one statement of a column batch.
     *
     * @param subject the subject
     * @param predicate the predicate
     * @param object the object
     * @param graph the graph, or null for the default graph (always null in TRIPLES streams)
     */
    protected void handleColumnRow(Object subject, Object predicate, Object object, Object graph) {
        throw new RdfProtoDeserializationError("Unexpected statement in stream.");
    }

    protected void handleTriple(RdfTriple triple) {
        throw new RdfProtoDeserializationError("Unexpected triple row in stream.");
    }

    protected void handleQuad(RdfQuad quad) {
        throw new RdfProtoDeserializationError("Unexpected quad row in stream.");
    }

    protected void handleGraphStart(RdfGraphStart graphStart) {
        throw new RdfProtoDeserializationError("Unexpected start of graph in stream.");
    }

    protected void handleGraphEnd() {
        throw new RdfProtoDeserializationError("Unexpected end of graph in stream.");
    }

    /**
     * A decoder that reads TRIPLES streams and outputs a sequence of triples.
     * <p>
     * Do not instantiate this class directly. Instead use factory methods in
     * ConverterFactory implementations.
     */
    public static final class TriplesDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final RdfHandler.TripleHandler<TNode> protoHandler;

        public TriplesDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.TripleHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        public void ingestRow(RdfStreamRow row) {
            ingestRowInternal(row);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (opts.getPhysicalType() != PhysicalStreamType.TRIPLES) {
                throw new RdfProtoDeserializationError("Incoming stream type is not TRIPLES.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void ingestColumns(RdfColumnBatch columns) {
            ingestColumnsInternal(columns);
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            protoHandler.handleTriple(
                convertSubjectTermWrapped(triple),
                convertPredicateTermWrapped(triple),
                convertObjectTermWrapped(triple)
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void handleColumnRow(Object subject, Object predicate, Object object, Object graph) {
            protoHandler.handleTriple((TNode) subject, (TNode) predicate, (TNode) object);
        }
    }

    /**
     * A decoder that reads QUADS streams and outputs a sequence of quads.
     * <p>
     * Do not instantiate this class directly. Instead use factory methods in
     * ConverterFactory implementations.
     */
    public static final class QuadsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final RdfHandler.QuadHandler<TNode> protoHandler;

        public QuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.QuadHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        public void ingestRow(RdfStreamRow row) {
            ingestRowInternal(row);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (opts.getPhysicalType() != PhysicalStreamType.QUADS) {
                throw new RdfProtoDeserializationError("Incoming stream type is not QUADS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void ingestColumns(RdfColumnBatch columns) {
            ingestColumnsInternal(columns);
        }

        @Override
        protected void handleQuad(RdfQuad quad) {
            protoHandler.handleQuad(
                convertSubjectTermWrapped(quad),
                convertPredicateTermWrapped(quad),
                convertObjectTermWrapped(quad),
                convertGraphTermWrapped(quad)
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void handleColumnRow(Object subject, Object predicate, Object object, Object graph) {
            protoHandler.handleQuad(
                (TNode) subject,
                (TNode) predicate,
                (TNode) object,
                graph == null ? defaultGraphNode() : (TNode) graph
            );
        }
    }

    /**
     * A decoder that reads GRAPHS streams and outputs a flat sequence of quads.
     * <p>
     * Do not instantiate this class directly. Instead use factory methods in
     * ConverterFactory implementations.
     */
    public static final class GraphsAsQuadsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final RdfHandler.QuadHandler<TNode> protoHandler;

        // Sometimes, like in jena, the default graph is represented as a null, so we cannot use the null check on
        // the graph term.
        private boolean currentGraphStarted = false;
        private TNode currentGraph = null;

        public GraphsAsQuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.QuadHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        public void ingestRow(RdfStreamRow row) {
            ingestRowInternal(row);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (opts.getPhysicalType() != PhysicalStreamType.GRAPHS) {
                throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void handleGraphStart(RdfGraphStart graphStart) {
            currentGraphStarted = true;
            currentGraph = convertGraphTerm(graphStart.getGraph());
        }

        @Override
        protected void handleGraphEnd() {
            currentGraphStarted = false;
            currentGraph = null;
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            if (!currentGraphStarted) {
                throw new RdfProtoDeserializationError("Triple in stream without preceding graph start.");
            }

            protoHandler.handleQuad(
                convertSubjectTermWrapped(triple),
                convertPredicateTermWrapped(triple),
                convertObjectTermWrapped(triple),
                currentGraph
            );
        }
    }

    /**
     * A decoder that reads GRAPHS streams and outputs a sequence of graphs.
     * Each graph is emitted as soon as the producer signals that it's complete.
     * <p>
     * Do not instantiate this class directly. Instead use factory methods in
     * ConverterFactory implementations.
     */
    public static final class GraphsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final RdfHandler.GraphHandler<TNode> protoHandler;
        private TNode currentGraph = null;

        public GraphsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.GraphHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        public void ingestRow(RdfStreamRow row) {
            ingestRowInternal(row);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (opts.getPhysicalType() != PhysicalStreamType.GRAPHS) {
                throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void handleGraphStart(RdfGraphStart graphStart) {
            currentGraph = convertGraphTerm(graphStart.getGraph());
            protoHandler.handleGraphStart(currentGraph);
        }

        @Override
        protected void handleGraphEnd() {
            if (currentGraph == null) {
                throw new RdfProtoDeserializationError("End of graph encountered before a start.");
            }

            currentGraph = null;
            protoHandler.handleGraphEnd();
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            var subject = convertSubjectTermWrapped(triple);
            var predicate = convertPredicateTermWrapped(triple);
            var object = convertObjectTermWrapped(triple);
            protoHandler.handleTriple(subject, predicate, object);
        }
    }

    /**
     * A decoder that reads streams of any type and outputs a sequence of triples or quads.
     * <p>
     * The type of the stream is detected automatically based on the options row,
     * which must be at the start of the stream. If the options row is not present or the stream changes its type
     * in the middle, an error is thrown.
     * <p>
     * Do not instantiate this class directly. Instead use factory methods in
     * ConverterFactory implementations.
     */
    public static final class AnyStatementDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final RdfHandler.AnyStatementHandler<TNode> protoHandler;
        private ProtoDecoderImpl<TNode, TDatatype> delegateDecoder = this;

        public AnyStatementDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.AnyStatementHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        public RdfStreamOptions getStreamOptions() {
            if (delegateDecoder != this) {
                return delegateDecoder.getStreamOptions();
            }

            return null;
        }

        @Override
        public void ingestRow(RdfStreamRow row) {
            // Initially this will direct calls to this decoder. Once we have the options row, we will
            // switch to the appropriate delegate decoder.
            // This is faster than checking the options every time.
            delegateDecoder.ingestRowInternal(row);
        }

        @Override
        protected void ingestColumns(RdfColumnBatch columns) {
            if (delegateDecoder == this) {
                throw new RdfProtoDeserializationError("Stream options are not set.");
            }
            delegateDecoder.ingestColumnsInternal(columns);
        }

        @Override
        protected void handleOptions(RdfStreamOptions options) {
            // Reset the logical type to UNSPECIFIED to ignore checking if it's supported by the inner decoder
            final var newSupportedOptions = supportedOptions.clone().setLogicalType(LogicalStreamType.UNSPECIFIED);

            checkCompatibility(options, newSupportedOptions);
            if (delegateDecoder != this) {
                return;
            }

            switch (options.getPhysicalType()) {
                case TRIPLES -> delegateDecoder = new TriplesDecoder<>(converter, protoHandler, newSupportedOptions);
                case QUADS -> delegateDecoder = new QuadsDecoder<>(converter, protoHandler, newSupportedOptions);
                case GRAPHS -> delegateDecoder = new GraphsAsQuadsDecoder<>(
                    converter,
                    protoHandler,
                    newSupportedOptions
                );
                default -> throw new RdfProtoDeserializationError("Incoming physical stream type is not recognized.");
            }
            // Replay the options row to the new decoder
            delegateDecoder.ingestRowInternal(RdfStreamRow.newInstance().setOptions(options));
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            throw new RdfProtoDeserializationError("Stream options are not set.");
        }

        @Override
        protected void handleQuad(RdfQuad quad) {
            throw new RdfProtoDeserializationError("Stream options are not set.");
        }
    }
}
