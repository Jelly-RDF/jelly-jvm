package eu.neverblink.jelly.core.patch;

import com.google.protobuf.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.patch.internal.PatchDecoderImpl;
import eu.neverblink.jelly.core.patch.internal.PatchEncoderImpl;
import eu.neverblink.jelly.core.proto.v1.RdfPatchOptions;

/**
 * Factory for creating RDF-Patch encoders and decoders.
 * <p>
 * You should implement this trait by passing a ConverterFactory for the RDF library you are using.
 * It's probably going to work best as a global `object`.
 *
 * @param <TNode> Type of RDF nodes in the RDF library
 * @param <TDatatype> Type of RDF datatypes in the RDF library
 * @param <TEncoderConverter> Implementation of ProtoEncoderConverter for a given RDF library.
 * @param <TDecoderConverter> Implementation of ProtoDecoderConverter for a given RDF library.
 */
@ExperimentalApi
public abstract class JellyPatchConverterFactory<
    TNode,
    TDatatype,
    TEncoderConverter extends ProtoEncoderConverter<TNode>,
    TDecoderConverter extends ProtoDecoderConverter<TNode, TDatatype>
> {

    private final JellyConverterFactory<TNode, TDatatype, TEncoderConverter, TDecoderConverter> converterFactory;

    protected JellyPatchConverterFactory(
        JellyConverterFactory<TNode, TDatatype, TEncoderConverter, TDecoderConverter> converterFactory
    ) {
        this.converterFactory = converterFactory;
    }

    /**
     * Create a new [[patch.PatchEncoder]] with the given parameters.
     *
     * @param params parameters for the encoder
     * @return encoder
     */
    public final PatchEncoder<TNode> encoder(PatchEncoder.Params params) {
        return new PatchEncoderImpl<>(converterFactory.encoderConverter(), params);
    }

    /**
     * Create a new PatchDecoder that decodes Jelly-Patch streams with statement type TRIPLES.
     *
     * @param handler handler for the decoded triples
     * @param supportedOptions supported options for the decoder
     * @return decoder
     */
    public PatchDecoder triplesDecoder(
        PatchHandler.TriplePatchHandler<TNode> handler,
        RdfPatchOptions supportedOptions
    ) {
        return new PatchDecoderImpl.TriplesDecoder<>(converterFactory.decoderConverter(), handler, supportedOptions);
    }

    /**
     * Create a new PatchDecoder that decodes Jelly-Patch streams with statement type QUADS.
     *
     * @param handler handler for the decoded quads
     * @param supportedOptions supported options for the decoder
     * @return decoder
     */
    public PatchDecoder quadsDecoder(PatchHandler.QuadPatchHandler<TNode> handler, RdfPatchOptions supportedOptions) {
        return new PatchDecoderImpl.QuadsDecoder<>(converterFactory.decoderConverter(), handler, supportedOptions);
    }

    /**
     * Create a new PatchDecoder that decodes Jelly-Patch streams of any statement type.
     *
     * @param handler handler for the decoded statements
     * @param supportedOptions supported options for the decoder
     * @return decoder
     */
    public PatchDecoder anyStatementDecoder(
        PatchHandler.AnyPatchHandler<TNode> handler,
        RdfPatchOptions supportedOptions
    ) {
        return new PatchDecoderImpl.AnyStatementDecoder<>(
            converterFactory.decoderConverter(),
            handler,
            supportedOptions
        );
    }
}
