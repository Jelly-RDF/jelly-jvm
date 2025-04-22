package eu.neverblink.jelly.core.internal;

import static eu.neverblink.jelly.core.JellyOptions.*;

import eu.neverblink.jelly.core.*;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import eu.neverblink.jelly.core.proto.v1.PhysicalStreamType;
import eu.neverblink.jelly.core.proto.v1.RdfDatatypeEntry;
import eu.neverblink.jelly.core.proto.v1.RdfGraphStart;
import eu.neverblink.jelly.core.proto.v1.RdfNamespaceDeclaration;
import eu.neverblink.jelly.core.proto.v1.RdfQuad;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamRow;
import eu.neverblink.jelly.core.proto.v1.RdfTriple;

/**
 * Base class for stateful decoders of protobuf RDF streams.
 *
 * @param <TNode>     the type of the node
 * @param <TDatatype> the type of the datatype
 * @see ProtoDecoder the base (extendable) interface.
 * @see ProtoDecoderBase for common methods shared by all decoders.
 */
public sealed class ProtoDecoderImpl<TNode, TDatatype> extends ProtoDecoder<TNode, TDatatype> {

    protected final RdfHandler<TNode> protoHandler;
    protected final RdfStreamOptions supportedOptions;

    private RdfStreamOptions currentOptions = null;

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
        if (currentOptions != null) {
            return;
        }

        this.currentOptions = options;
    }

    @Override
    public void ingestRow(RdfStreamRow row) {
        if (row == null) {
            throw new RdfProtoDeserializationError("Row kind is not set.");
        }

        switch (row.getRowCase()) {
            case OPTIONS -> handleOptions(row.getOptions());
            case NAME -> nameDecoder.updateNames(row.getName());
            case PREFIX -> nameDecoder.updatePrefixes(row.getPrefix());
            case DATATYPE -> handleDatatype(row.getDatatype());
            case NAMESPACE -> handleNamespace(row.getNamespace());
            case TRIPLE -> handleTriple(row.getTriple());
            case QUAD -> handleQuad(row.getQuad());
            case GRAPH_START -> handleGraphStart(row.getGraphStart());
            case GRAPH_END -> handleGraphEnd();
            case ROW_NOT_SET -> throw new RdfProtoDeserializationError("Row kind is not set.");
        }
    }

    protected void handleOptions(RdfStreamOptions options) {
        checkCompatibility(options, supportedOptions);
        setStreamOptions(options);
    }

    protected void handleDatatype(RdfDatatypeEntry datatype) {
        datatypeLookup.update(datatype.getId(), converter.makeDatatype(datatype.getValue()));
    }

    protected void handleNamespace(RdfNamespaceDeclaration namespace) {
        final var iri = namespace.getValue();
        protoHandler.handleNamespace(namespace.getName(), nameDecoder.decode(iri.getPrefixId(), iri.getNameId()));
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

        private final RdfHandler.TripleStatementHandler<TNode> protoHandler;

        public TriplesDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.TripleStatementHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)) {
                throw new RdfProtoDeserializationError("Incoming stream type is not TRIPLES.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            final var tripleTerm = RdfTerm.from(triple);
            protoHandler.handleTriple(
                convertSubjectTermWrapped(tripleTerm.subject()),
                convertPredicateTermWrapped(tripleTerm.predicate()),
                convertObjectTermWrapped(tripleTerm.object())
            );
        }
    }

    /**
     * A decoder that reads QUADS streams and outputs a sequence of quads.
     * <p>
     * Do not instantiate this class directly. Instead use factory methods in
     * ConverterFactory implementations.
     */
    public static final class QuadsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final RdfHandler.QuadStatementHandler<TNode> protoHandler;

        public QuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.QuadStatementHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)) {
                throw new RdfProtoDeserializationError("Incoming stream type is not QUADS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void handleQuad(RdfQuad quad) {
            final var quadTerm = RdfTerm.from(quad);
            protoHandler.handleQuad(
                convertSubjectTermWrapped(quadTerm.subject()),
                convertPredicateTermWrapped(quadTerm.predicate()),
                convertObjectTermWrapped(quadTerm.object()),
                convertGraphTermWrapped(quadTerm.graph())
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

        private final RdfHandler.QuadStatementHandler<TNode> protoHandler;
        private TNode currentGraph = null;

        public GraphsAsQuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.QuadStatementHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)) {
                throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void handleGraphStart(RdfGraphStart graphStart) {
            final var graphStartTerm = RdfTerm.from(graphStart);
            currentGraph = convertGraphTerm(graphStartTerm.graph());
        }

        @Override
        protected void handleGraphEnd() {
            currentGraph = null;
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            if (currentGraph == null) {
                throw new RdfProtoDeserializationError("Triple in stream without preceding graph start.");
            }

            final var tripleTerm = RdfTerm.from(triple);
            protoHandler.handleQuad(
                convertSubjectTermWrapped(tripleTerm.subject()),
                convertPredicateTermWrapped(tripleTerm.predicate()),
                convertObjectTermWrapped(tripleTerm.object()),
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

        private final RdfHandler.GraphStatementHandler<TNode> protoHandler;
        private TNode currentGraph = null;

        public GraphsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            RdfHandler.GraphStatementHandler<TNode> protoHandler,
            RdfStreamOptions supportedOptions
        ) {
            super(converter, protoHandler, supportedOptions);
            this.protoHandler = protoHandler;
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)) {
                throw new RdfProtoDeserializationError("Incoming stream type is not GRAPHS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected void handleGraphStart(RdfGraphStart graphStart) {
            final var graphStartTerm = RdfTerm.from(graphStart);
            currentGraph = convertGraphTerm(graphStartTerm.graph());
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
            var tripleTerm = RdfTerm.from(triple);
            var subject = convertSubjectTermWrapped(tripleTerm.subject());
            var predicate = convertPredicateTermWrapped(tripleTerm.predicate());
            var object = convertObjectTermWrapped(tripleTerm.object());
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
        private ProtoDecoderImpl<TNode, TDatatype> delegateDecoder = null;

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
            if (delegateDecoder != null) {
                return delegateDecoder.getStreamOptions();
            }

            return null;
        }

        @Override
        public void ingestRow(RdfStreamRow row) {
            if (row.hasOptions()) {
                handleOptions(row.getOptions());
                delegateDecoder.ingestRow(row);
                return;
            }

            if (delegateDecoder == null) {
                throw new RdfProtoDeserializationError("Stream options are not set.");
            }

            delegateDecoder.ingestRow(row);
        }

        @Override
        protected void handleOptions(RdfStreamOptions options) {
            final var newSupportedOptions = supportedOptions
                .toBuilder()
                .setLogicalType(LogicalStreamType.LOGICAL_STREAM_TYPE_UNSPECIFIED)
                .build();

            checkCompatibility(options, newSupportedOptions);
            if (delegateDecoder != null) {
                return;
            }

            switch (options.getPhysicalType()) {
                case PHYSICAL_STREAM_TYPE_TRIPLES -> delegateDecoder = new TriplesDecoder<>(
                    converter,
                    protoHandler,
                    options
                );
                case PHYSICAL_STREAM_TYPE_QUADS -> delegateDecoder = new QuadsDecoder<>(
                    converter,
                    protoHandler,
                    options
                );
                case PHYSICAL_STREAM_TYPE_GRAPHS -> delegateDecoder = new GraphsAsQuadsDecoder<>(
                    converter,
                    protoHandler,
                    options
                );
                default -> throw new RdfProtoDeserializationError("Incoming physical stream type is not recognized.");
            }
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            delegateDecoder.handleTriple(triple);
        }

        @Override
        protected void handleQuad(RdfQuad quad) {
            delegateDecoder.handleQuad(quad);
        }

        @Override
        protected void handleGraphStart(RdfGraphStart graphStart) {
            delegateDecoder.handleGraphStart(graphStart);
        }

        @Override
        protected void handleGraphEnd() {
            delegateDecoder.handleGraphEnd();
        }
    }
}
