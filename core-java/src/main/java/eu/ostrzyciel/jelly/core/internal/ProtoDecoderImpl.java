package eu.ostrzyciel.jelly.core.internal;

import static eu.ostrzyciel.jelly.core.JellyOptions.*;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType;
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType;
import eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart;
import eu.ostrzyciel.jelly.core.proto.v1.RdfQuad;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import eu.ostrzyciel.jelly.core.proto.v1.RdfTriple;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public sealed class ProtoDecoderImpl<TNode, TDatatype> extends ProtoDecoder<TNode, TDatatype> {

    protected final BiConsumer<String, TNode> namespaceHandler;
    protected final RdfStreamOptions supportedOptions;

    private RdfStreamOptions currentOptions = null;

    public ProtoDecoderImpl(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        BiConsumer<String, TNode> namespaceHandler,
        RdfStreamOptions supportedOptions
    ) {
        super(converter);
        this.namespaceHandler = namespaceHandler;
        this.supportedOptions = supportedOptions;
    }

    @Override
    protected int getNameTableSize() {
        if (currentOptions == null) {
            return SMALL_NAME_TABLE_SIZE;
        }

        return currentOptions.getMaxNameTableSize();
    }

    @Override
    protected int getPrefixTableSize() {
        if (currentOptions == null) {
            return SMALL_PREFIX_TABLE_SIZE;
        }

        return currentOptions.getMaxPrefixTableSize();
    }

    @Override
    protected int getDatatypeTableSize() {
        if (currentOptions == null) {
            return SMALL_DT_TABLE_SIZE;
        }

        return currentOptions.getMaxDatatypeTableSize();
    }

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
            case DATATYPE -> {
                final var dtRow = row.getDatatype();
                datatypeLookup.update(dtRow.getId(), converter.makeDatatype(dtRow.getValue()));
            }
            case NAMESPACE -> {
                final var nsRow = row.getNamespace();
                final var iri = nsRow.getValue();
                namespaceHandler.accept(nsRow.getName(), nameDecoder.decode(iri.getNameId(), iri.getPrefixId()));
            }
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

    protected void handleTriple(RdfTriple triple) {
        throw new RdfProtoDeserializationError("Unexpected triple row in stream.");
    }

    protected void handleQuad(RdfQuad quad) {
        throw new RdfProtoDeserializationError("Unexpected quad row in stream.");
    }

    protected void handleGraphStart(RdfGraphStart graphStart) {
        throw new RdfProtoDeserializationError("Unexpected graph start row in stream.");
    }

    protected void handleGraphEnd() {
        throw new RdfProtoDeserializationError("Unexpected graph end row in stream.");
    }

    public static final class TriplesDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final ProtoHandler.TripleProtoHandler<TNode> protoHandler;

        public TriplesDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            BiConsumer<String, TNode> nsHandler,
            RdfStreamOptions supportedOptions,
            ProtoHandler.TripleProtoHandler<TNode> protoHandler
        ) {
            super(converter, nsHandler, supportedOptions);
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

    public static final class QuadsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final ProtoHandler.QuadProtoHandler<TNode> protoHandler;

        public QuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            BiConsumer<String, TNode> nsHandler,
            RdfStreamOptions supportedOptions,
            ProtoHandler.QuadProtoHandler<TNode> protoHandler
        ) {
            super(converter, nsHandler, supportedOptions);
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
                convertGraphTerm(quadTerm.graph())
            );
        }
    }

    public static final class GraphsAsQuadsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final ProtoHandler.QuadProtoHandler<TNode> protoHandler;
        private TNode currentGraph = null;

        public GraphsAsQuadsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            BiConsumer<String, TNode> nsHandler,
            RdfStreamOptions supportedOptions,
            ProtoHandler.QuadProtoHandler<TNode> protoHandler
        ) {
            super(converter, nsHandler, supportedOptions);
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

    public static final class GraphsDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final ProtoHandler.GraphProtoHandler<TNode> protoHandler;
        private TNode currentGraph = null;
        private final List<TNode> buffer = new ArrayList<>();

        public GraphsDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            BiConsumer<String, TNode> nsHandler,
            RdfStreamOptions supportedOptions,
            ProtoHandler.GraphProtoHandler<TNode> protoHandler
        ) {
            super(converter, nsHandler, supportedOptions);
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
            emitBuffer();
            buffer.clear();
            currentGraph = convertGraphTerm(RdfTerm.from(graphStart).graph());
        }

        @Override
        protected void handleGraphEnd() {
            emitBuffer();
            buffer.clear();
            currentGraph = null;
        }

        @Override
        protected void handleTriple(RdfTriple triple) {
            if (currentGraph == null) {
                throw new RdfProtoDeserializationError("Triple in stream without preceding graph start.");
            }

            buffer.add(convertTriple(RdfTerm.from(triple)));
        }

        private void emitBuffer() {
            if (buffer.isEmpty()) {
                return;
            }

            if (currentGraph == null) {
                throw new RdfProtoDeserializationError("End of graph encountered before a start.");
            }

            protoHandler.handleGraph(currentGraph, buffer);
        }
    }

    public static final class AnyDecoder<TNode, TDatatype> extends ProtoDecoderImpl<TNode, TDatatype> {

        private final ProtoHandler.AnyProtoHandler<TNode> protoHandler;
        private ProtoDecoderImpl<TNode, TDatatype> delegateDecoder = null;

        public AnyDecoder(
            ProtoDecoderConverter<TNode, TDatatype> converter,
            BiConsumer<String, TNode> namespaceHandler,
            RdfStreamOptions supportedOptions,
            ProtoHandler.AnyProtoHandler<TNode> protoHandler
        ) {
            super(converter, namespaceHandler, supportedOptions);
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
                    namespaceHandler,
                    options,
                    protoHandler
                );
                case PHYSICAL_STREAM_TYPE_QUADS -> delegateDecoder = new QuadsDecoder<>(
                    converter,
                    namespaceHandler,
                    options,
                    protoHandler
                );
                case PHYSICAL_STREAM_TYPE_GRAPHS -> delegateDecoder = new GraphsAsQuadsDecoder<>(
                    converter,
                    namespaceHandler,
                    options,
                    protoHandler
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
