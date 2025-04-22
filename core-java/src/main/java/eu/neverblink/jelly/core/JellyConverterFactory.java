package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.internal.ProtoDecoderImpl;
import eu.neverblink.jelly.core.internal.ProtoEncoderImpl;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;

/**
 * "Main" interface to be implemented by RDF conversion modules (e.g., for Jena and RDF4J).
 * Exposes factory methods for building protobuf encoders and decoders.
 * <p>
 * This should typically be implemented as an object. You should also provide a package-scoped given for your
 * implementation so that users can easily make use of the connector in the stream package.
 *
 * @param <TNode> Type of RDF nodes in the RDF library
 * @param <TDatatype> Type of RDF datatypes in the RDF library
 * @param <TEncoderConverter> Implementation of ProtoEncoderConverter for a given RDF library.
 * @param <TDecoderConverter> Implementation of ProtoDecoderConverter for a given RDF library.
 */
public abstract class JellyConverterFactory<
    TNode,
    TDatatype,
    TEncoderConverter extends ProtoEncoderConverter<TNode>,
    TDecoderConverter extends ProtoDecoderConverter<TNode, TDatatype>
> {

    /**
     * To be implemented by subclasses. Returns an instance of ProtoEncoderConverter for the RDF library.
     */
    protected abstract TEncoderConverter encoderConverter();

    /**
     * To be implemented by subclasses. Returns an instance of ProtoDecoderConverter for the RDF library.
     */
    protected abstract TDecoderConverter decoderConverter();

    /**
     * Create a new ProtoEncoder.
     * @param params Parameters for the encoder.
     * @return encoder
     */
    public final ProtoEncoder<TNode> encoder(ProtoEncoder.Params params) {
        return new ProtoEncoderImpl<>(encoderConverter(), params);
    }

    /**
     * Create a new TriplesDecoder.
     * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
     *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
     *                         type), you should always use this.defaultSupportedOptions.withXxx.
     *                         namespace prefix (without a colon), the second is the IRI node.
     * @param tripleProtoHandler the handler to use for decoding triples
     * @return decoder
     */
    public final ProtoDecoder<TNode, TDatatype> triplesDecoder(
        RdfHandler.TripleStatementHandler<TNode> tripleProtoHandler,
        RdfStreamOptions supportedOptions
    ) {
        return new ProtoDecoderImpl.TriplesDecoder<>(decoderConverter(), tripleProtoHandler, supportedOptions);
    }

    /**
     * Create a new QuadsDecoder.
     * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
     *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
     *                         type), you should always use this.defaultSupportedOptions.withXxx.
     * @param quadProtoHandler the handler to use for decoding quads
     * @return decoder
     */
    public final ProtoDecoder<TNode, TDatatype> quadsDecoder(
        RdfHandler.QuadStatementHandler<TNode> quadProtoHandler,
        RdfStreamOptions supportedOptions
    ) {
        return new ProtoDecoderImpl.QuadsDecoder<>(decoderConverter(), quadProtoHandler, supportedOptions);
    }

    /**
     * Create a new GraphsAsQuadsDecoder.
     * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
     *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
     *                         type), you should always use this.defaultSupportedOptions.withXxx.
     * @param graphProtoHandler the handler to use for decoding graphs
     * @return decoder
     */
    public final ProtoDecoder<TNode, TDatatype> graphsAsQuadsDecoder(
        RdfHandler.QuadStatementHandler<TNode> graphProtoHandler,
        RdfStreamOptions supportedOptions
    ) {
        return new ProtoDecoderImpl.GraphsAsQuadsDecoder<>(decoderConverter(), graphProtoHandler, supportedOptions);
    }

    /**
     * Create a new GraphsDecoder.
     * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
     *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
     *                         type), you should always use this.defaultSupportedOptions.withXxx.
     * @param graphProtoHandler the handler to use for decoding graphs
     * @return decoder
     */
    public final ProtoDecoder<TNode, TDatatype> graphsDecoder(
        RdfHandler.GraphStatementHandler<TNode> graphProtoHandler,
        RdfStreamOptions supportedOptions
    ) {
        return new ProtoDecoderImpl.GraphsDecoder<>(decoderConverter(), graphProtoHandler, supportedOptions);
    }

    /**
     * Create a new AnyStatementDecoder.
     * @param supportedOptions maximum supported options for the decoder. If not provided, this.defaultSupportedOptions
     *                         will be used. If you want to modify this (e.g., to specify an expected logical stream
     *                         type), you should always use this.defaultSupportedOptions.withXxx.
     * @param anyProtoHandler the handler to use for decoding any statements
     * @return decoder
     */
    public final ProtoDecoder<TNode, TDatatype> anyDecoder(
        RdfHandler.AnyStatementHandler<TNode> anyProtoHandler,
        RdfStreamOptions supportedOptions
    ) {
        return new ProtoDecoderImpl.AnyStatementDecoder<>(decoderConverter(), anyProtoHandler, supportedOptions);
    }
}
