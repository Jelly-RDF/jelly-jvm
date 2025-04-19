package eu.ostrzyciel.jelly.core.internal;

import static eu.ostrzyciel.jelly.core.JellyOptions.*;

import eu.ostrzyciel.jelly.core.*;
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType;
import eu.ostrzyciel.jelly.core.proto.v1.RdfGraphStart;
import eu.ostrzyciel.jelly.core.proto.v1.RdfQuad;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;
import eu.ostrzyciel.jelly.core.proto.v1.RdfTriple;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public sealed class ProtoDecoderImpl<TNode, TDatatype, TTriple, TQuad, TOut>
    extends ProtoDecoder<TNode, TDatatype, TTriple, TQuad, TOut> {

    protected final BiConsumer<String, TNode> namespaceHandler;
    private RdfStreamOptions supportedOptions;

    public ProtoDecoderImpl(
        Class<TDatatype> datatypeClass,
        ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
        NameDecoder<TNode> nameDecoder,
        BiConsumer<String, TNode> namespaceHandler,
        RdfStreamOptions supportedOptions
    ) {
        super(datatypeClass, converter, nameDecoder);
        this.namespaceHandler = namespaceHandler;
        this.supportedOptions = supportedOptions;
    }

    @Override
    protected int getNameTableSize() {
        return Optional.ofNullable(supportedOptions)
            .map(RdfStreamOptions::getMaxNameTableSize)
            .orElse(SMALL_NAME_TABLE_SIZE);
    }

    @Override
    protected int getPrefixTableSize() {
        return Optional.ofNullable(supportedOptions)
            .map(RdfStreamOptions::getMaxPrefixTableSize)
            .orElse(SMALL_PREFIX_TABLE_SIZE);
    }

    @Override
    protected int getDatatypeTableSize() {
        return Optional.ofNullable(supportedOptions)
            .map(RdfStreamOptions::getMaxDatatypeTableSize)
            .orElse(SMALL_DT_TABLE_SIZE);
    }

    @Override
    public Optional<RdfStreamOptions> getStreamOptions() {
        return Optional.ofNullable(supportedOptions);
    }

    public void setStreamOptions(RdfStreamOptions options) {
        this.supportedOptions = options;
    }

    @Override
    public TOut ingestRowFlat(RdfStreamRow row) {
        if (row == null) {
            throw new JellyException.RdfProtoDeserializationError("Row kind is not set.");
        }

        return switch (row.getRowCase()) {
            case OPTIONS -> {
                handleOptions(row.getOptions());
                yield null;
            }
            case NAME -> {
                nameDecoder.updateNames(row.getName());
                yield null;
            }
            case PREFIX -> {
                nameDecoder.updatePrefixes(row.getPrefix());
                yield null;
            }
            case DATATYPE -> {
                var dtRow = row.getDatatype();
                datatypeLookup.update(dtRow.getId(), converter.makeDatatype(dtRow.getValue()));
                yield null;
            }
            case TRIPLE -> handleTriple(row.getTriple());
            case QUAD -> handleQuad(row.getQuad());
            case GRAPH_START -> handleGraphStart(row.getGraphStart());
            case GRAPH_END -> handleGraphEnd();
            case NAMESPACE -> {
                var nsRow = row.getNamespace();
                var iri = nsRow.getValue();
                namespaceHandler.accept(nsRow.getName(), nameDecoder.decode(iri.getNameId(), iri.getPrefixId()));
                yield null;
            }
            case ROW_NOT_SET -> throw new JellyException.RdfProtoDeserializationError("Row kind is not set.");
        };
    }

    protected void handleOptions(RdfStreamOptions opts) {
        checkCompatibility(opts, supportedOptions);
        setStreamOptions(opts);
    }

    protected TOut handleTriple(RdfTriple triple) {
        throw new JellyException.RdfProtoDeserializationError("Unexpected triple row in stream.");
    }

    protected TOut handleQuad(RdfQuad quad) {
        throw new JellyException.RdfProtoDeserializationError("Unexpected quad row in stream.");
    }

    protected TOut handleGraphStart(RdfGraphStart graphStart) {
        throw new JellyException.RdfProtoDeserializationError("Unexpected graph start row in stream.");
    }

    protected TOut handleGraphEnd() {
        throw new JellyException.RdfProtoDeserializationError("Unexpected graph end row in stream.");
    }

    public static final class TriplesDecoder<TNode, TDatatype, TTriple, TQuad>
        extends ProtoDecoderImpl<TNode, TDatatype, TTriple, TQuad, TTriple> {

        public TriplesDecoder(
            Class<TDatatype> datatypeClass,
            ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
            NameDecoder<TNode> nameDecoder,
            RdfStreamOptions supportedOptions,
            BiConsumer<String, TNode> nsHandler
        ) {
            super(datatypeClass, converter, nameDecoder, nsHandler, supportedOptions);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)) {
                throw new JellyException.RdfProtoDeserializationError("Incoming stream type is not TRIPLES.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected TTriple handleTriple(RdfTriple triple) {
            return convertTriple(RdfTerm.from(triple));
        }
    }

    public static final class QuadsDecoder<TNode, TDatatype, TTriple, TQuad>
        extends ProtoDecoderImpl<TNode, TDatatype, TTriple, TQuad, TQuad> {

        public QuadsDecoder(
            Class<TDatatype> datatypeClass,
            ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
            NameDecoder<TNode> nameDecoder,
            RdfStreamOptions supportedOptions,
            BiConsumer<String, TNode> nsHandler
        ) {
            super(datatypeClass, converter, nameDecoder, nsHandler, supportedOptions);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)) {
                throw new JellyException.RdfProtoDeserializationError("Incoming stream type is not QUADS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected TQuad handleQuad(RdfQuad quad) {
            return convertQuad(RdfTerm.from(quad));
        }
    }

    public static final class GraphsAsQuadsDecoder<TNode, TDatatype, TTriple, TQuad>
        extends ProtoDecoderImpl<TNode, TDatatype, TTriple, TQuad, TQuad> {

        private TNode currentGraph = null;

        public GraphsAsQuadsDecoder(
            Class<TDatatype> datatypeClass,
            ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
            NameDecoder<TNode> nameDecoder,
            RdfStreamOptions supportedOptions,
            BiConsumer<String, TNode> nsHandler
        ) {
            super(datatypeClass, converter, nameDecoder, nsHandler, supportedOptions);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)) {
                throw new JellyException.RdfProtoDeserializationError("Incoming stream type is not GRAPHS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected TQuad handleGraphStart(RdfGraphStart graphStart) {
            var graphStartTerm = RdfTerm.from(graphStart);
            currentGraph = convertGraphTerm(graphStartTerm.graph());
            return null;
        }

        @Override
        protected TQuad handleGraphEnd() {
            currentGraph = null;
            return null;
        }

        @Override
        protected TQuad handleTriple(RdfTriple triple) {
            if (currentGraph == null) {
                throw new JellyException.RdfProtoDeserializationError(
                    "Triple in stream without preceding graph start."
                );
            }

            var tripleTerm = RdfTerm.from(triple);
            return converter.makeQuad(
                convertTermWrapped(tripleTerm.subject(), lastSubject),
                convertTermWrapped(tripleTerm.predicate(), lastPredicate),
                convertTermWrapped(tripleTerm.object(), lastObject),
                currentGraph
            );
        }
    }

    public record GraphsDecoderOut<TNode, TTriple>(TNode graph, List<TTriple> triples) {}

    public static final class GraphsDecoder<TNode, TDatatype, TTriple, TQuad>
        extends ProtoDecoderImpl<TNode, TDatatype, TTriple, TQuad, GraphsDecoderOut<TNode, TTriple>> {

        private TNode currentGraph = null;
        private List<TTriple> buffer = new ArrayList<>();

        public GraphsDecoder(
            Class<TDatatype> datatypeClass,
            ProtoDecoderConverter<TNode, TDatatype, TTriple, TQuad> converter,
            NameDecoder<TNode> nameDecoder,
            RdfStreamOptions supportedOptions,
            BiConsumer<String, TNode> nsHandler
        ) {
            super(datatypeClass, converter, nameDecoder, nsHandler, supportedOptions);
        }

        @Override
        protected void handleOptions(RdfStreamOptions opts) {
            if (!opts.getPhysicalType().equals(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)) {
                throw new JellyException.RdfProtoDeserializationError("Incoming stream type is not GRAPHS.");
            }
            super.handleOptions(opts);
        }

        @Override
        protected GraphsDecoderOut<TNode, TTriple> handleGraphStart(RdfGraphStart graphStart) {
            var toEmit = emitBuffer();
            buffer = new ArrayList<>();
            currentGraph = convertGraphTerm(RdfTerm.from(graphStart).graph());
            return toEmit;
        }

        @Override
        protected GraphsDecoderOut<TNode, TTriple> handleGraphEnd() {
            var toEmit = emitBuffer();
            buffer = new ArrayList<>();
            currentGraph = null;
            return toEmit;
        }

        @Override
        protected GraphsDecoderOut<TNode, TTriple> handleTriple(RdfTriple triple) {
            if (currentGraph == null) {
                throw new JellyException.RdfProtoDeserializationError(
                    "Triple in stream without preceding graph start."
                );
            }

            buffer.add(convertTriple(RdfTerm.from(triple)));
            return null;
        }

        private GraphsDecoderOut<TNode, TTriple> emitBuffer() {
            if (buffer.isEmpty()) {
                return null;
            } else if (currentGraph == null) {
                throw new JellyException.RdfProtoDeserializationError("End of graph encountered before a start.");
            } else {
                return new GraphsDecoderOut<>(currentGraph, List.copyOf(buffer));
            }
        }
    }
    // TODO: AnyStatementDecoder - no idea how to implement Triple Or Quad, we are not in scala world
}
