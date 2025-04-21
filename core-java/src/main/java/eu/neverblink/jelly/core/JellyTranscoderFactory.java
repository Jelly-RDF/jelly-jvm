package eu.neverblink.jelly.core;

import eu.neverblink.jelly.core.internal.ProtoTranscoderImpl;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;

/**
 * Factory for creating ProtoTranscoder instances.
 */
public interface JellyTranscoderFactory {
    /**
     * Fast transcoder suitable for merging multiple input streams into one.
     * This variant DOES NOT check the input options of the consumed streams. This should be therefore only used
     * when the input is fully trusted. Otherwise, an attacker could cause a DoS by sending a stream with large lookups.
     *
     * @param outputOptions options for the output stream. This MUST have the physical stream type set.
     * @return ProtoTranscoder
     */
    default ProtoTranscoder fastMergingTranscoderUnsafe(RdfStreamOptions outputOptions) {
        return new ProtoTranscoderImpl(null, outputOptions);
    }

    /**
     * Fast transcoder suitable for merging multiple input streams into one.
     * This variant does check the input options of the consumed streams, so it is SAFE to use with untrusted input.
     *
     * @param supportedInputOptions maximum allowable options for the input streams
     * @param outputOptions options for the output stream. This MUST have the physical stream type set.
     * @return ProtoTranscoder
     */
    default ProtoTranscoder fastMergingTranscoder(
        RdfStreamOptions supportedInputOptions,
        RdfStreamOptions outputOptions
    ) {
        return new ProtoTranscoderImpl(supportedInputOptions, outputOptions);
    }
}
