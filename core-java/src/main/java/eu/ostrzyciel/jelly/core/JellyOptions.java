package eu.ostrzyciel.jelly.core;

import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType;
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions;
import eu.ostrzyciel.jelly.core.utils.LogicalStreamTypeUtils;

public class JellyOptions {

    private JellyOptions() {}

    public static final int BIG_NAME_TABLE_SIZE = 4000;
    public static final int BIG_PREFIX_TABLE_SIZE = 150;
    public static final int BIG_DT_TABLE_SIZE = 32;

    public static final int SMALL_NAME_TABLE_SIZE = 128;
    public static final int SMALL_PREFIX_TABLE_SIZE = 16;
    public static final int SMALL_DT_TABLE_SIZE = 16;

    public static final RdfStreamOptions BIG_STRICT = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .build();

    public static final RdfStreamOptions BIG_GENERALIZED = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .build();

    public static final RdfStreamOptions BIG_RDF_STAR = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setRdfStar(true)
        .build();

    public static final RdfStreamOptions BIG_ALL_FEATURES = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(BIG_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(BIG_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(BIG_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .build();

    public static final RdfStreamOptions SMALL_STRICT = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .build();

    public static final RdfStreamOptions SMALL_GENERALIZED = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .build();

    public static final RdfStreamOptions SMALL_RDF_STAR = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setRdfStar(true)
        .build();

    public static final RdfStreamOptions SMALL_ALL_FEATURES = RdfStreamOptions.newBuilder()
        .setMaxNameTableSize(SMALL_NAME_TABLE_SIZE)
        .setMaxPrefixTableSize(SMALL_PREFIX_TABLE_SIZE)
        .setMaxDatatypeTableSize(SMALL_DT_TABLE_SIZE)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .build();

    public static final RdfStreamOptions DEFAULT_SUPPORTED_OPTIONS = RdfStreamOptions.newBuilder()
        .setVersion(JellyConstants.PROTO_VERSION)
        .setGeneralizedStatements(true)
        .setRdfStar(true)
        .setMaxNameTableSize(4096)
        .setMaxPrefixTableSize(1024)
        .setMaxDatatypeTableSize(256)
        .build();

    public static void checkCompatibility(RdfStreamOptions requestedOptions, RdfStreamOptions supportedOptions) {
        checkBaseCompatibility(requestedOptions, supportedOptions);
        checkLogicalStreamType(requestedOptions, supportedOptions.getLogicalType());
    }

    private static void checkBaseCompatibility(RdfStreamOptions requestedOptions, RdfStreamOptions supportedOptions) {
        if (
            requestedOptions.getVersion() > supportedOptions.getVersion() ||
            requestedOptions.getVersion() > JellyConstants.PROTO_VERSION
        ) {
            throw new RdfProtoDeserializationError(
                "Unsupported proto version: %s. Was expecting at most version %s. This library version supports up to version %s.".formatted(
                        requestedOptions.getVersion(),
                        supportedOptions.getVersion(),
                        JellyConstants.PROTO_VERSION
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

    private static void checkLogicalStreamType(RdfStreamOptions options, LogicalStreamType expectedLogicalType) {
        final var logicalType = options.getLogicalType();
        final var baseLogicalType = LogicalStreamTypeUtils.toBaseType(logicalType);
        final var physicalType = options.getPhysicalType();

        final var conflict =
            switch (baseLogicalType) {
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
            throw new RdfProtoDeserializationError(
                "Logical stream type %s is incompatible with physical stream type %s.".formatted(
                        logicalType,
                        physicalType
                    )
            );
        }

        if (!LogicalStreamTypeUtils.isEqualOrSubtypeOf(logicalType, expectedLogicalType)) {
            throw new RdfProtoDeserializationError(
                "Expected logical stream type %s, got %s. %s is not a subtype of %s.".formatted(
                        expectedLogicalType,
                        logicalType,
                        logicalType,
                        expectedLogicalType
                    )
            );
        }
    }
}
