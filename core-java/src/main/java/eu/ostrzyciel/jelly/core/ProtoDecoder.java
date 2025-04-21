package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.internal.ProtoDecoderBase;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamRow;

/**
 * Base extendable interface for decoders of protobuf RDF streams.
 * <p>
 * See the implementation in ProtoDecoderImpl.
 *
 * @param <TNode> The type of the node.
 * @param <TDatatype> The type of the datatype.
 */
public abstract class ProtoDecoder<TNode, TDatatype> extends ProtoDecoderBase<TNode, TDatatype> {

    /**
     * Constructor.
     *
     * @param converter the converter to use
     */
    protected ProtoDecoder(ProtoDecoderConverter<TNode, TDatatype> converter) {
        super(converter);
    }

    /**
     * Options for this stream.
     * @return options if the decoder has encountered the stream options, None otherwise.
     */
    protected abstract RdfStreamOptions getStreamOptions();

    /**
     * Ingest a row from the stream.
     *
     * @param row row to ingest
     */
    public abstract void ingestRow(RdfStreamRow row);
}
