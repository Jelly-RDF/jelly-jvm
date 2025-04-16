package eu.ostrzyciel.jelly.core.utils;

import eu.ostrzyciel.jelly.core.proto.v1.Rdf;
import java.util.Optional;

public class LogicalStreamTypeUtils {

    private static final String STAX_PREFIX = "http://www.w3.org/2001/rdf-stax#";

    private LogicalStreamTypeUtils() {}

    public static Rdf.LogicalStreamType toBaseType(Rdf.LogicalStreamType logicalType) {
        return Rdf.LogicalStreamType.forNumber(logicalType.getNumber() % 10);
    }

    public static boolean isEqualOrSubtypeOf(Rdf.LogicalStreamType logicalType, Rdf.LogicalStreamType other) {
        return logicalType == other || logicalType.getNumber() % 10 == other.getNumber();
    }

    public static Optional<String> getRdfStaxType(Rdf.LogicalStreamType logicalType) {
        return switch (logicalType) {
            case LOGICAL_STREAM_TYPE_FLAT_TRIPLES -> Optional.of(STAX_PREFIX + "flatTripleStream");
            case LOGICAL_STREAM_TYPE_FLAT_QUADS -> Optional.of(STAX_PREFIX + "flatQuadStream");
            case LOGICAL_STREAM_TYPE_GRAPHS -> Optional.of(STAX_PREFIX + "graphStream");
            case LOGICAL_STREAM_TYPE_SUBJECT_GRAPHS -> Optional.of(STAX_PREFIX + "subjectGraphStream");
            case LOGICAL_STREAM_TYPE_DATASETS -> Optional.of(STAX_PREFIX + "datasetStream");
            case LOGICAL_STREAM_TYPE_NAMED_GRAPHS -> Optional.of(STAX_PREFIX + "namedGraphStream");
            case LOGICAL_STREAM_TYPE_TIMESTAMPED_NAMED_GRAPHS -> Optional.of(
                STAX_PREFIX + "timestampedNamedGraphStream"
            );
            default -> Optional.empty();
        };
    }

    public static Optional<Rdf.LogicalStreamType> fromOntologyIri(String iri) {
        if (!iri.startsWith(STAX_PREFIX)) {
            return Optional.empty();
        }

        String typeName = iri.substring(STAX_PREFIX.length());
        return switch (typeName) {
            case "flatTripleStream" -> Optional.of(Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES);
            case "flatQuadStream" -> Optional.of(Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_QUADS);
            case "graphStream" -> Optional.of(Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_GRAPHS);
            case "subjectGraphStream" -> Optional.of(Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_SUBJECT_GRAPHS);
            case "datasetStream" -> Optional.of(Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_DATASETS);
            case "namedGraphStream" -> Optional.of(Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_NAMED_GRAPHS);
            case "timestampedNamedGraphStream" -> Optional.of(
                Rdf.LogicalStreamType.LOGICAL_STREAM_TYPE_TIMESTAMPED_NAMED_GRAPHS
            );
            default -> Optional.empty();
        };
    }
}
