package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFFormatVariant;
import org.apache.jena.riot.RiotException;
import org.apache.jena.sparql.util.Context;

/**
 * Subclass of RDFFormatVariant to pass Jelly's options to the encoder.
 *
 * @see RDFFormatVariant
 */
public final class JellyFormatVariant extends RDFFormatVariant {

    public static final RdfStreamOptions DEFAULT_OPTIONS = JellyOptions.SMALL_ALL_FEATURES;
    public static final int DEFAULT_FRAME_SIZE = 256;
    public static final boolean DEFAULT_ENABLE_NAMESPACE_DECLARATIONS = false;
    public static final boolean DEFAULT_DELIMITED = true;
    // Constant name for all variants of the Jelly format, as all writers can handle all variants.
    public static final String VARIANT_NAME = "Variant";

    private final RdfStreamOptions options;
    private final boolean enableNamespaceDeclarations;
    private final boolean isDelimited;
    private final int frameSize;

    public static Builder builder() {
        return new Builder();
    }

    public static JellyFormatVariant getDefault() {
        return builder().build();
    }

    public static final class Builder {

        private RdfStreamOptions options = DEFAULT_OPTIONS;
        private boolean enableNamespaceDeclarations = DEFAULT_ENABLE_NAMESPACE_DECLARATIONS;
        private boolean isDelimited = DEFAULT_DELIMITED;
        private int frameSize = DEFAULT_FRAME_SIZE;

        private Builder() {}

        /**
         * Set the options for the Jelly format variant.
         * @param options Jelly options
         * @return this
         */
        public Builder options(RdfStreamOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Set whether to include namespace declarations in the output.
         * @param enableNamespaceDeclarations whether to include namespace declarations
         * @return this
         */
        public Builder enableNamespaceDeclarations(boolean enableNamespaceDeclarations) {
            this.enableNamespaceDeclarations = enableNamespaceDeclarations;
            return this;
        }

        /**
         * Set whether to write the output as delimited frames.
         * <p>
         * Note: files saved to disk are
         * recommended to be delimited, for better interoperability with other implementations.
         * In a non-delimited file you can have ONLY ONE FRAME. If the input data is large,
         * this will lead to an out-of-memory error. So, this makes sense only for small data.
         * **Disable this only if you know what you are doing.**
         *
         * @param isDelimited whether to write the output as delimited frames
         *
         * @return this
         */
        public Builder isDelimited(boolean isDelimited) {
            this.isDelimited = isDelimited;
            return this;
        }

        /**
         * Set the size of each RdfStreamFrame, in rows.
         * @param frameSize size of each RdfStreamFrame, in rows
         * @return this
         */
        public Builder frameSize(int frameSize) {
            this.frameSize = frameSize;
            return this;
        }

        public JellyFormatVariant build() {
            return new JellyFormatVariant(options, enableNamespaceDeclarations, isDelimited, frameSize);
        }
    }

    /**
     * Constructor for JellyFormatVariant.
     *
     * @param options Jelly options
     * @param frameSize size of each RdfStreamFrame, in rows
     * @param enableNamespaceDeclarations whether to include namespace declarations in the output
     * @param isDelimited whether to write the output as delimited frames. Note: files saved to disk are
     *                    recommended to be delimited, for better interoperability with other implementations.
     *                    In a non-delimited file you can have ONLY ONE FRAME. If the input data is large,
     *                    this will lead to an out-of-memory error. So, this makes sense only for small data.
     *                    **Disable this only if you know what you are doing.**
     */
    private JellyFormatVariant(
        RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        boolean isDelimited,
        int frameSize
    ) {
        // Constant, because all writers can handle all variants
        super(VARIANT_NAME);
        this.options = options;
        this.enableNamespaceDeclarations = enableNamespaceDeclarations;
        this.isDelimited = isDelimited;
        this.frameSize = frameSize;
    }

    /**
     * Extract the format variant from the RDFFormat or use the default.
     */
    public static JellyFormatVariant getVariant(RDFFormat syntaxForm) {
        if (syntaxForm.getVariant() instanceof JellyFormatVariant jellyFormatVariant) {
            return jellyFormatVariant;
        } else {
            return builder().build();
        }
    }

    /**
     * Make a new Jelly format variant with the information from the context.
     * @param context context
     * @return updated variant
     */
    public JellyFormatVariant withContext(Context context) {
        // Use the preset if set
        final var presetName = context.get(JellyLanguage.SYMBOL_PRESET, "");
        RdfStreamOptions preset;
        if (!presetName.isEmpty()) {
            preset = JellyLanguage.PRESETS.get(presetName);
            if (preset == null) {
                throw new RiotException(
                    "Unknown Jelly preset: %s. Available presets: %s".formatted(
                            presetName,
                            String.join(", ", JellyLanguage.PRESETS.keySet())
                        )
                );
            }
        } else {
            preset = this.options;
        }

        return new JellyFormatVariant(
            context.get(JellyLanguage.SYMBOL_STREAM_OPTIONS, preset),
            context.isTrue(JellyLanguage.SYMBOL_ENABLE_NAMESPACE_DECLARATIONS) || this.isEnableNamespaceDeclarations(),
            context.isTrueOrUndef(JellyLanguage.SYMBOL_DELIMITED_OUTPUT),
            context.getInt(JellyLanguage.SYMBOL_FRAME_SIZE, this.getFrameSize())
        );
    }

    public RdfStreamOptions getOptions() {
        return options;
    }

    public boolean isEnableNamespaceDeclarations() {
        return enableNamespaceDeclarations;
    }

    public boolean isDelimited() {
        return isDelimited;
    }

    public int getFrameSize() {
        return frameSize;
    }

    public JellyFormatVariant withOptions(RdfStreamOptions options) {
        return new JellyFormatVariant(options, enableNamespaceDeclarations, isDelimited, frameSize);
    }
}
