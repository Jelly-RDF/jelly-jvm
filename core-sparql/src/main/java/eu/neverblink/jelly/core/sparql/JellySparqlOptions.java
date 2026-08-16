package eu.neverblink.jelly.core.sparql;

import static eu.neverblink.jelly.core.internal.BaseJellyOptions.*;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;

/**
 * Utilities for working with SparqlResultsOptions.
 */
@ExperimentalApi
public final class JellySparqlOptions {

    private JellySparqlOptions() {}

    public static final SparqlResultsOptions BIG = SparqlResultsOptions.newInstance()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setVersion(JellySparqlConstants.PROTO_VERSION);

    public static final SparqlResultsOptions SMALL = SparqlResultsOptions.newInstance()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setVersion(JellySparqlConstants.PROTO_VERSION);

    public static final SparqlResultsOptions DEFAULT_SUPPORTED_OPTIONS = BIG;

    /**
     * Convert Jelly-RDF RdfStreamOptions to Jelly-SPARQL SparqlResultsOptions.
     * <p>
     * Only the lookup table sizes are carried over. Fields that are not relevant to
     * SPARQL results (physical and logical stream types, RDF-star, generalized statements)
     * are ignored.
     *
     * @param opt RdfStreamOptions
     * @return SparqlResultsOptions
     */
    public static SparqlResultsOptions fromJellyOptions(RdfStreamOptions opt) {
        return SparqlResultsOptions.newInstance()
            .setMaxNameTableSize(opt.getMaxNameTableSize())
            .setMaxPrefixTableSize(opt.getMaxPrefixTableSize())
            .setMaxDatatypeTableSize(opt.getMaxDatatypeTableSize())
            .setVersion(JellySparqlConstants.PROTO_VERSION);
    }

    /**
     * Checks if the requested stream options are supported. Throws an exception if not.
     *
     * @param requestedOptions Requested options of the stream.
     * @param supportedOptions Options that can be safely supported.
     * @throws RdfProtoDeserializationError on validation error
     */
    public static void checkCompatibility(
        SparqlResultsOptions requestedOptions,
        SparqlResultsOptions supportedOptions
    ) {
        if (
            requestedOptions.getVersion() > supportedOptions.getVersion() ||
            requestedOptions.getVersion() > JellySparqlConstants.PROTO_VERSION
        ) {
            throw new RdfProtoDeserializationError(
                "Unsupported proto version: %s. Was expecting at most version %s. This library version supports up to version %s.".formatted(
                    requestedOptions.getVersion(),
                    supportedOptions.getVersion(),
                    JellySparqlConstants.PROTO_VERSION
                )
            );
        }

        checkTableSize(
            "name",
            requestedOptions.getMaxNameTableSize(),
            supportedOptions.getMaxNameTableSize(),
            MIN_NAME_TABLE_SIZE
        );
        checkTableSize("prefix", requestedOptions.getMaxPrefixTableSize(), supportedOptions.getMaxPrefixTableSize(), 0);
        checkTableSize(
            "datatype",
            requestedOptions.getMaxDatatypeTableSize(),
            supportedOptions.getMaxDatatypeTableSize(),
            0
        );
    }

    private static void checkTableSize(String name, int size, int supportedSize, int minSize) {
        if (size > supportedSize) {
            throw new RdfProtoDeserializationError(
                "The stream uses a %s table size of %s, which is larger than the maximum supported size of %s.".formatted(
                    name,
                    size,
                    supportedSize
                )
            );
        }
        if (size < minSize) {
            throw new RdfProtoDeserializationError(
                "The stream uses a %s table size of %s, which is smaller than the minimum supported size of %s.".formatted(
                    name,
                    size,
                    minSize
                )
            );
        }
    }
}
