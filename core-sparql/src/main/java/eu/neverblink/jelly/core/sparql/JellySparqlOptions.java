package eu.neverblink.jelly.core.sparql;

import eu.neverblink.jelly.core.ExperimentalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions;
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions;

/**
 * Utilities for working with SparqlResultsOptions.
 * <p>
 * The lookup table sizes here are Jelly-SPARQL's own and deliberately larger than the Jelly-RDF
 * ones: a solution sequence is a projection, so one frame of results tends to touch far more
 * distinct terms per row than one RDF statement does. The working set of a frame must fit in the
 * tables (see the notes in sparql.proto).
 */
@ExperimentalApi
public final class JellySparqlOptions {

    private JellySparqlOptions() {}

    /**
     * Smallest name table a Jelly-SPARQL stream may declare. There is no such floor for the
     * prefix and datatype tables – those may be disabled outright, with a size of 0.
     */
    public static final int MIN_NAME_TABLE_SIZE = 128;

    public static final int SMALL_NAME_TABLE_SIZE = 256;
    public static final int BIG_NAME_TABLE_SIZE = 4096;
    public static final int MAX_NAME_TABLE_SIZE = BIG_NAME_TABLE_SIZE * 4;

    public static final int SMALL_PREFIX_TABLE_SIZE = 64;
    public static final int BIG_PREFIX_TABLE_SIZE = 1024;
    public static final int MAX_PREFIX_TABLE_SIZE = BIG_PREFIX_TABLE_SIZE * 4;

    public static final int SMALL_DT_TABLE_SIZE = 32;
    public static final int BIG_DT_TABLE_SIZE = 64;
    public static final int MAX_DT_TABLE_SIZE = 256;

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

    /**
     * The largest lookup tables a Jelly-SPARQL stream may declare at all. A reader must not be
     * asked to support more than this, whatever its supported options say.
     */
    public static final SparqlResultsOptions MAX = SparqlResultsOptions.newInstance()
        .setMaxNameTableSize(MAX_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(MAX_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(MAX_DT_TABLE_SIZE)
        .setVersion(JellySparqlConstants.PROTO_VERSION);

    /**
     * What a decoder accepts unless told otherwise. Bounded well below MAX, so that a stream
     * cannot make the reader allocate large tables unless it was asked to allow that.
     */
    public static final SparqlResultsOptions DEFAULT_SUPPORTED_OPTIONS = BIG;

    /**
     * Convert Jelly-RDF RdfStreamOptions to Jelly-SPARQL SparqlResultsOptions.
     * <p>
     * Only the lookup table sizes are carried over. Fields that are not relevant to
     * SPARQL results (physical and logical stream types, RDF-star, generalized statements)
     * are ignored.
     * <p>
     * The sizes are taken as they are, so the result is not necessarily a valid Jelly-SPARQL
     * configuration: Jelly-RDF uses smaller tables than Jelly-SPARQL does, and allows name tables
     * well below {@link #MIN_NAME_TABLE_SIZE}.
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

        // The MAX sizes cap the supported ones: a reader cannot opt into tables larger than
        // Jelly-SPARQL allows, however generous its supported options are.
        checkTableSize(
            "name",
            requestedOptions.getMaxNameTableSize(),
            Math.min(supportedOptions.getMaxNameTableSize(), MAX_NAME_TABLE_SIZE),
            MIN_NAME_TABLE_SIZE
        );
        checkTableSize(
            "prefix",
            requestedOptions.getMaxPrefixTableSize(),
            Math.min(supportedOptions.getMaxPrefixTableSize(), MAX_PREFIX_TABLE_SIZE),
            0
        );
        checkTableSize(
            "datatype",
            requestedOptions.getMaxDatatypeTableSize(),
            Math.min(supportedOptions.getMaxDatatypeTableSize(), MAX_DT_TABLE_SIZE),
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
