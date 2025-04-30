package eu.neverblink.jelly.convert.jena.riot;

import eu.neverblink.jelly.core.JellyOptions;
import org.apache.jena.riot.RDFFormat;

/**
 * Pre-defined serialization format variants for Jelly.
 */
public final class JellyFormat {

    private JellyFormat() {}

    public static final RDFFormat JELLY_SMALL_STRICT = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.SMALL_STRICT)
    );

    public static final RDFFormat JELLY_SMALL_GENERALIZED = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.SMALL_GENERALIZED)
    );

    public static final RDFFormat JELLY_SMALL_RDF_STAR = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.SMALL_RDF_STAR)
    );

    public static final RDFFormat JELLY_SMALL_ALL_FEATURES = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.SMALL_ALL_FEATURES)
    );

    public static final RDFFormat JELLY_BIG_STRICT = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.BIG_STRICT)
    );

    public static final RDFFormat JELLY_BIG_GENERALIZED = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.BIG_GENERALIZED)
    );

    public static final RDFFormat JELLY_BIG_RDF_STAR = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.BIG_RDF_STAR)
    );

    public static final RDFFormat JELLY_BIG_ALL_FEATURES = new RDFFormat(
        JellyLanguage.JELLY_LANGUAGE,
        new JellyFormatVariant(JellyOptions.BIG_ALL_FEATURES)
    );
}
