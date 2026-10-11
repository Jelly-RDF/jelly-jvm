package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.core.JellyOptions;
import org.apache.jena.riot.RDFFormat;

/**
 * Pre-defined serialization format variants for Jelly: the SMALL and BIG presets of
 * {@link JellyOptions}, see {@link JellyLanguage#PRESETS}.
 */
public final class JellyFormat {

    private JellyFormat() {}

    public static final RDFFormat JELLY_SMALL_STRICT;

    /**
     * @deprecated In Jelly-RDF 1.2, which Jena writes by default, this is the same as
     * {@link #JELLY_SMALL_STRICT}: the generalized RDF and RDF-star flags only apply to Jelly-RDF 1.0
     * and 1.1 output, which will be removed in Jelly-JVM 5.0.0. To write Jelly-RDF 1.0 or 1.1, build
     * your own format with {@link JellyFormatVariant#builder()}, with the protocol version set in the
     * options.
     */
    @Deprecated(forRemoval = true)
    public static final RDFFormat JELLY_SMALL_GENERALIZED;

    /**
     * @deprecated In Jelly-RDF 1.2, which Jena writes by default, this is the same as
     * {@link #JELLY_SMALL_STRICT}: the generalized RDF and RDF-star flags only apply to Jelly-RDF 1.0
     * and 1.1 output, which will be removed in Jelly-JVM 5.0.0. To write Jelly-RDF 1.0 or 1.1, build
     * your own format with {@link JellyFormatVariant#builder()}, with the protocol version set in the
     * options.
     */
    @Deprecated(forRemoval = true)
    public static final RDFFormat JELLY_SMALL_RDF_STAR;

    /**
     * @deprecated In Jelly-RDF 1.2, which Jena writes by default, this is the same as
     * {@link #JELLY_SMALL_STRICT}: the generalized RDF and RDF-star flags only apply to Jelly-RDF 1.0
     * and 1.1 output, which will be removed in Jelly-JVM 5.0.0. To write Jelly-RDF 1.0 or 1.1, build
     * your own format with {@link JellyFormatVariant#builder()}, with the protocol version set in the
     * options.
     */
    @Deprecated(forRemoval = true)
    public static final RDFFormat JELLY_SMALL_ALL_FEATURES;

    public static final RDFFormat JELLY_BIG_STRICT;

    /**
     * @deprecated In Jelly-RDF 1.2, which Jena writes by default, this is the same as
     * {@link #JELLY_BIG_STRICT}: the generalized RDF and RDF-star flags only apply to Jelly-RDF 1.0
     * and 1.1 output, which will be removed in Jelly-JVM 5.0.0. To write Jelly-RDF 1.0 or 1.1, build
     * your own format with {@link JellyFormatVariant#builder()}, with the protocol version set in the
     * options.
     */
    @Deprecated(forRemoval = true)
    public static final RDFFormat JELLY_BIG_GENERALIZED;

    /**
     * @deprecated In Jelly-RDF 1.2, which Jena writes by default, this is the same as
     * {@link #JELLY_BIG_STRICT}: the generalized RDF and RDF-star flags only apply to Jelly-RDF 1.0
     * and 1.1 output, which will be removed in Jelly-JVM 5.0.0. To write Jelly-RDF 1.0 or 1.1, build
     * your own format with {@link JellyFormatVariant#builder()}, with the protocol version set in the
     * options.
     */
    @Deprecated(forRemoval = true)
    public static final RDFFormat JELLY_BIG_RDF_STAR;

    /**
     * @deprecated In Jelly-RDF 1.2, which Jena writes by default, this is the same as
     * {@link #JELLY_BIG_STRICT}: the generalized RDF and RDF-star flags only apply to Jelly-RDF 1.0
     * and 1.1 output, which will be removed in Jelly-JVM 5.0.0. To write Jelly-RDF 1.0 or 1.1, build
     * your own format with {@link JellyFormatVariant#builder()}, with the protocol version set in the
     * options.
     */
    @Deprecated(forRemoval = true)
    public static final RDFFormat JELLY_BIG_ALL_FEATURES;

    static {
        // Force initialize the language before initializing the formats
        JellyLanguage.register();

        JELLY_SMALL_STRICT = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.SMALL_STRICT).build()
        );
        JELLY_SMALL_GENERALIZED = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.SMALL_GENERALIZED).build()
        );
        JELLY_SMALL_RDF_STAR = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.SMALL_RDF_STAR).build()
        );
        JELLY_SMALL_ALL_FEATURES = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.SMALL_ALL_FEATURES).build()
        );
        JELLY_BIG_STRICT = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.BIG_STRICT).build()
        );
        JELLY_BIG_GENERALIZED = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.BIG_GENERALIZED).build()
        );
        JELLY_BIG_RDF_STAR = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.BIG_RDF_STAR).build()
        );
        JELLY_BIG_ALL_FEATURES = new RDFFormat(
            JellyLanguage.JELLY,
            JellyFormatVariant.builder().options(JellyOptions.BIG_ALL_FEATURES).build()
        );
    }
}
