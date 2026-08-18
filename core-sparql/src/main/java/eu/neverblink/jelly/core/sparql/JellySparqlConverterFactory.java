package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.JellyConverterFactory;
import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.ProtoEncoderConverter;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;
import eu.neverblink.jelly.core.sparql.internal.SparqlDecoderImpl;
import eu.neverblink.jelly.core.sparql.internal.SparqlEncoderImpl;

/**
 * Factory for creating Jelly-SPARQL encoders and decoders.
 * <p>
 * You should implement this class by passing a JellyConverterFactory for the RDF library you are
 * using. It's probably going to work best as a singleton.
 *
 * @param <TNode> Type of RDF nodes in the RDF library
 * @param <TDatatype> Type of RDF datatypes in the RDF library
 * @param <TEncoderConverter> Implementation of ProtoEncoderConverter for a given RDF library.
 * @param <TDecoderConverter> Implementation of ProtoDecoderConverter for a given RDF library.
 */
@ExperimentalApi
public abstract class JellySparqlConverterFactory<
    TNode,
    TDatatype,
    TEncoderConverter extends ProtoEncoderConverter<TNode>,
    TDecoderConverter extends ProtoDecoderConverter<TNode, TDatatype>
> {

    private final JellyConverterFactory<TNode, TDatatype, TEncoderConverter, TDecoderConverter> converterFactory;

    protected JellySparqlConverterFactory(
        JellyConverterFactory<TNode, TDatatype, TEncoderConverter, TDecoderConverter> converterFactory
    ) {
        this.converterFactory = converterFactory;
    }

    /**
     * Create a new {@link SparqlEncoder} with the given parameters.
     *
     * @param params parameters for the encoder
     * @return encoder
     */
    public final SparqlEncoder<TNode> encoder(SparqlEncoder.Params params) {
        return new SparqlEncoderImpl<>(converterFactory.encoderConverter(), params);
    }

    /**
     * Create a new {@link SparqlDecoder} pushing the decoded results to the given handler.
     *
     * @param handler handler for the decoded results
     * @param supportedOptions supported options for the decoder
     * @return decoder
     */
    public final SparqlDecoder decoder(SparqlResultsHandler<TNode> handler, SparqlResultsOptions supportedOptions) {
        return new SparqlDecoderImpl<>(converterFactory.decoderConverter(), handler, supportedOptions);
    }
}
