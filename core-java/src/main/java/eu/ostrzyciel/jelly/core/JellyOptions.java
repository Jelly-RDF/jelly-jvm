package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.Rdf;
import eu.ostrzyciel.jelly.core.utils.LogicalStreamTypeUtils;

public class JellyOptions {

    private JellyOptions() {}

    public static final int BIG_NAME_TABLE_SIZE = 4000;
    public static final int BIG_PREFIX_TABLE_SIZE = 150;
    public static final int BIG_DT_TABLE_SIZE = 32;

    public static final int SMALL_NAME_TABLE_SIZE = 128;
    public static final int SMALL_PREFIX_TABLE_SIZE = 16;
    public static final int SMALL_DT_TABLE_SIZE = 16;

    public static final Rdf.RdfStreamOptions BIG_STRICT = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .build();

    public static final Rdf.RdfStreamOptions BIG_GENERALIZED = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .build();

    public static final Rdf.RdfStreamOptions BIG_RDF_STAR = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setRdfStar(true)
        .build();

    public static final Rdf.RdfStreamOptions BIG_ALL_FEATURES = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .build();

    public static final Rdf.RdfStreamOptions SMALL_STRICT = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .build();

    public static final Rdf.RdfStreamOptions SMALL_GENERALIZED = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .build();

    public static final Rdf.RdfStreamOptions SMALL_RDF_STAR = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setRdfStar(true)
        .build();

    public static final Rdf.RdfStreamOptions SMALL_ALL_FEATURES = Rdf.RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .build();

    public static final Rdf.RdfStreamOptions DEFAULT_SUPPORTED_OPTIONS = Rdf.RdfStreamOptions.newBuilder()
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .setMaxNameTableSize(4096)
        .setMaxPrefixTableSize(1024)
        .setMaxDatatypeTableSize(256)
        .build();

    public static void checkCompatibility(
        Rdf.RdfStreamOptions requestedOptions,
        Rdf.RdfStreamOptions supportedOptions
    ) {
        checkBaseCompatibility(requestedOptions, supportedOptions, JellyConstants.PROTO_VERSION);
        checkLogicalStreamType(requestedOptions, supportedOptions.getLogicalType());
    }

    private static void checkBaseCompatibility(
        Rdf.RdfStreamOptions requestedOptions,
        Rdf.RdfStreamOptions supportedOptions,
        int systemSupportedVersion
    ) {
        if (
            requestedOptions.getVersion() > supportedOptions.getVersion() ||
            requestedOptions.getVersion() > systemSupportedVersion
        ) {
            throw new IllegalArgumentException(
                ("Unsupported proto version: %s. Was expecting at most version %s. " +
                    "This library version supports up to version %s.").formatted(
                        requestedOptions.getVersion(),
                        supportedOptions.getVersion(),
                        systemSupportedVersion
                    )
            );
        }
        if (requestedOptions.getGeneralizedStatements() && !supportedOptions.getGeneralizedStatements()) {
            throw new IllegalArgumentException(
                "The stream uses generalized statements, which are not supported. " +
                "Either disable generalized statements or enable them in the supported options."
            );
        }
        if (requestedOptions.getRdfStar() && !supportedOptions.getRdfStar()) {
            throw new IllegalArgumentException(
                "The stream uses RDF-star, which is not supported. Either disable" +
                " RDF-star or enable it in the supported options."
            );
        }

        checkTableSize("Name", requestedOptions.getMaxNameTableSize(), supportedOptions.getMaxNameTableSize(), 8);
        checkTableSize("Prefix", requestedOptions.getMaxPrefixTableSize(), supportedOptions.getMaxPrefixTableSize());
        checkTableSize(
            "Datatype",
            requestedOptions.getMaxDatatypeTableSize(),
            supportedOptions.getMaxDatatypeTableSize()
        );
    }

    private static void checkTableSize(String name, int size, int supportedSize, int minSize) {
        if (size > supportedSize) {
            throw new IllegalArgumentException(
                "The stream uses a " +
                name.toLowerCase() +
                " table size of " +
                size +
                ", which is larger than the maximum supported size of " +
                supportedSize +
                "."
            );
        }
        if (size < minSize) {
            throw new IllegalArgumentException(
                "The stream uses a " +
                name.toLowerCase() +
                " table size of " +
                size +
                ", which is smaller than the minimum supported size of " +
                minSize +
                "."
            );
        }
    }

    private static void checkTableSize(String name, int size, int supportedSize) {
        checkTableSize(name, size, supportedSize, 0);
    }

    private static void checkLogicalStreamType(
        Rdf.RdfStreamOptions options,
        Rdf.LogicalStreamType expectedLogicalType
    ) {
        var logicalType = options.getLogicalType();
        var physicalType = options.getPhysicalType();

        var conflict =
            switch (logicalType) {
                case LOGICAL_STREAM_TYPE_FLAT_TRIPLES, LOGICAL_STREAM_TYPE_GRAPHS -> switch (physicalType) {
                    case PHYSICAL_STREAM_TYPE_QUADS, PHYSICAL_STREAM_TYPE_GRAPHS -> true;
                    default -> false;
                };
                case LOGICAL_STREAM_TYPE_FLAT_QUADS, LOGICAL_STREAM_TYPE_DATASETS -> switch (physicalType) {
                    case PHYSICAL_STREAM_TYPE_TRIPLES -> true;
                    default -> false;
                };
                default -> false;
            };

        if (conflict) {
            throw new IllegalArgumentException(
                "Logical stream type %s is incompatible with physical stream type %s.".formatted(
                        logicalType,
                        options.getPhysicalType()
                    )
            );
        }

        if (!LogicalStreamTypeUtils.isEqualOrSubtypeOf(logicalType, expectedLogicalType)) {
            throw new IllegalArgumentException(
                "Logical stream type %s is incompatible with expected logical stream type %s.".formatted(
                        options.getLogicalType(),
                        expectedLogicalType
                    )
            );
        }
    }
}
