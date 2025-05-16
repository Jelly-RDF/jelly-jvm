package eu.neverblink.jelly.core.internal;

import eu.neverblink.jelly.core.InternalApi;
import eu.neverblink.jelly.core.RdfProtoDeserializationError;
import eu.neverblink.jelly.core.internal.proto.OptionsBase;

/**
 * BaseJellyOptions is a utility class that provides constants and methods for validating
 * compatibility of common Jelly options.
 * <p>
 * This class is not intended to be instantiated.
 * <p>
 * It contains constants for table sizes and methods to check compatibility of requested options
 * with supported options and the maximum supported proto version.
 */
@InternalApi
public class BaseJellyOptions {

    private BaseJellyOptions() {}

    public static final int BIG_NAME_TABLE_SIZE = 4000;
    public static final int BIG_PREFIX_TABLE_SIZE = 150;
    public static final int BIG_DT_TABLE_SIZE = 32;

    public static final int SMALL_NAME_TABLE_SIZE = 128;
    public static final int SMALL_PREFIX_TABLE_SIZE = 16;
    public static final int SMALL_DT_TABLE_SIZE = 16;

    /**
     * Minimum size of the name table, according to the spec.
     */
    public static final int MIN_NAME_TABLE_SIZE = 8;

    /**
     * Check if the requested options are compatible with the supported options and the system.
     *
     * @param requestedOptions requested options
     * @param supportedOptions supported options
     * @param maximalSupportedProtoVersion maximum supported proto version
     *
     * @throws RdfProtoDeserializationError on validation error
     */
    @InternalApi
    public static void checkBaseCompatibility(
        OptionsBase requestedOptions,
        OptionsBase supportedOptions,
        int maximalSupportedProtoVersion
    ) {
        if (
            requestedOptions.getVersion() > supportedOptions.getVersion() ||
            requestedOptions.getVersion() > maximalSupportedProtoVersion
        ) {
            throw new RdfProtoDeserializationError(
                "Unsupported proto version: %s. Was expecting at most version %s. This library version supports up to version %s.".formatted(
                        requestedOptions.getVersion(),
                        supportedOptions.getVersion(),
                        maximalSupportedProtoVersion
                    )
            );
        }
        if (requestedOptions.getGeneralizedStatements() && !supportedOptions.getGeneralizedStatements()) {
            throw new RdfProtoDeserializationError(
                "The stream uses generalized statements, which are not supported. " +
                "Either disable generalized statements or enable them in the supportedOptions."
            );
        }
        if (requestedOptions.getRdfStar() && !supportedOptions.getRdfStar()) {
            throw new RdfProtoDeserializationError(
                "The stream uses RDF-star, which is not supported. " +
                "Either disable RDF-star or enable it in the supportedOptions."
            );
        }

        checkTableSize(
            "Name",
            requestedOptions.getMaxNameTableSize(),
            supportedOptions.getMaxNameTableSize(),
            MIN_NAME_TABLE_SIZE
        );
        checkTableSize("Prefix", requestedOptions.getMaxPrefixTableSize(), supportedOptions.getMaxPrefixTableSize());
        checkTableSize(
            "Datatype",
            requestedOptions.getMaxDatatypeTableSize(),
            supportedOptions.getMaxDatatypeTableSize()
        );
    }

    /**
     * Checks if the table size is within the supported range.
     *
     * @param name Name of the table (for error messages).
     * @param size Size of the table.
     * @param supportedSize Maximum supported size of the table.
     * @param minSize Minimum supported size of the table.
     *
     * @throws RdfProtoDeserializationError if the table size is not within the supported range.
     */
    private static void checkTableSize(String name, int size, int supportedSize, int minSize) {
        if (size > supportedSize) {
            throw new RdfProtoDeserializationError(
                "The stream uses a %s table size of %s, which is larger than the maximum supported size of %s.".formatted(
                        name.toLowerCase(),
                        size,
                        supportedSize
                    )
            );
        }
        if (size < minSize) {
            throw new RdfProtoDeserializationError(
                "The stream uses a %s table size of %s, which is smaller than the minimum supported size of %s.".formatted(
                        name.toLowerCase(),
                        size,
                        minSize
                    )
            );
        }
    }

    private static void checkTableSize(String name, int size, int supportedSize) {
        checkTableSize(name, size, supportedSize, 0);
    }
}
