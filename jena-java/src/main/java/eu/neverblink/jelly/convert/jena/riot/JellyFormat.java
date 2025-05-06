package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.core.JellyOptions;
import org.apache.jena.riot.RDFFormat;

/**
 * Pre-defined serialization format variants for Jelly.
 */
public final class JellyFormat {

    private JellyFormat() {}

    public static final RDFFormat JELLY_SMALL_STRICT = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.SMALL_STRICT).build()
    );

    public static final RDFFormat JELLY_SMALL_GENERALIZED = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.SMALL_GENERALIZED).build()
    );

    public static final RDFFormat JELLY_SMALL_RDF_STAR = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.SMALL_RDF_STAR).build()
    );

    public static final RDFFormat JELLY_SMALL_ALL_FEATURES = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.SMALL_ALL_FEATURES).build()
    );

    public static final RDFFormat JELLY_BIG_STRICT = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.BIG_STRICT).build()
    );

    public static final RDFFormat JELLY_BIG_GENERALIZED = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.BIG_GENERALIZED).build()
    );

    public static final RDFFormat JELLY_BIG_RDF_STAR = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.BIG_RDF_STAR).build()
    );

    public static final RDFFormat JELLY_BIG_ALL_FEATURES = new RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(JellyOptions.BIG_ALL_FEATURES).build()
    );
}
