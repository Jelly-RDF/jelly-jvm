package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.core.JellyOptions;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import org.apache.jena.riot.RDFFormatVariant;

/**
 * Subclass of RDFFormatVariant to pass Jelly's options to the encoder.
 *
 * @see RDFFormatVariant
 */
public class JellyFormatVariant extends RDFFormatVariant {

    public static final RdfStreamOptions DEFAULT_OPTIONS = JellyOptions.SMALL_ALL_FEATURES;
    public static final int DEFAULT_FRAME_SIZE = 256;
    public static final boolean DEFAULT_ENABLE_NAMESPACE_DECLARATIONS = false;
    public static final boolean DEFAULT_DELIMITED = true;

    private final RdfStreamOptions options;
    private final boolean enableNamespaceDeclarations;
    private final boolean isDelimited;
    private final int frameSize;

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
    public JellyFormatVariant(
        RdfStreamOptions options,
        boolean enableNamespaceDeclarations,
        boolean isDelimited,
        int frameSize
    ) {
        super(options.getStreamName());
        this.options = options;
        this.enableNamespaceDeclarations = enableNamespaceDeclarations;
        this.isDelimited = isDelimited;
        this.frameSize = frameSize;
    }

    public JellyFormatVariant(boolean enableNamespaceDeclarations, boolean isDelimited, int frameSize) {
        this(DEFAULT_OPTIONS, enableNamespaceDeclarations, isDelimited, frameSize);
    }

    public JellyFormatVariant(RdfStreamOptions options) {
        this(options, DEFAULT_ENABLE_NAMESPACE_DECLARATIONS, DEFAULT_DELIMITED, DEFAULT_FRAME_SIZE);
    }

    public JellyFormatVariant() {
        this(DEFAULT_OPTIONS, DEFAULT_ENABLE_NAMESPACE_DECLARATIONS, DEFAULT_DELIMITED, DEFAULT_FRAME_SIZE);
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

    public JellyFormatVariant updateOptions(RdfStreamOptions options) {
        return new JellyFormatVariant(options, enableNamespaceDeclarations, isDelimited, frameSize);
    }
}
