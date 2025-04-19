package eu.ostrzyciel.jelly.core.utils;

import eu.ostrzyciel.jelly.core.ProtoDecoderConverter;
import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType;
import java.util.List;
import java.util.UUID;

public class LogicalStreamTypeUtils {

    private static final String STAX_PREFIX = "http://www.w3.org/2001/rdf-stax#";

    private LogicalStreamTypeUtils() {}

    public static LogicalStreamType toBaseType(LogicalStreamType logicalType) {
        return LogicalStreamType.forNumber(logicalType.getNumber() % 10);
    }

    public static boolean isEqualOrSubtypeOf(LogicalStreamType logicalType, LogicalStreamType other) {
        return logicalType == other || logicalType.getNumber() % 10 == other.getNumber();
    }

    public static String getRdfStaxType(LogicalStreamType logicalType) {
        return switch (logicalType) {
            case LOGICAL_STREAM_TYPE_FLAT_TRIPLES -> STAX_PREFIX + "flatTripleStream";
            case LOGICAL_STREAM_TYPE_FLAT_QUADS -> STAX_PREFIX + "flatQuadStream";
            case LOGICAL_STREAM_TYPE_GRAPHS -> STAX_PREFIX + "graphStream";
            case LOGICAL_STREAM_TYPE_SUBJECT_GRAPHS -> STAX_PREFIX + "subjectGraphStream";
            case LOGICAL_STREAM_TYPE_DATASETS -> STAX_PREFIX + "datasetStream";
            case LOGICAL_STREAM_TYPE_NAMED_GRAPHS -> STAX_PREFIX + "namedGraphStream";
            case LOGICAL_STREAM_TYPE_TIMESTAMPED_NAMED_GRAPHS -> STAX_PREFIX + "timestampedNamedGraphStream";
            default -> null;
        };
    }

    public static LogicalStreamType fromOntologyIri(String iri) {
        if (!iri.startsWith(STAX_PREFIX)) {
            return null;
        }

        String typeName = iri.substring(STAX_PREFIX.length());
        return switch (typeName) {
            case "flatTripleStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_TRIPLES;
            case "flatQuadStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_FLAT_QUADS;
            case "graphStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_GRAPHS;
            case "subjectGraphStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_SUBJECT_GRAPHS;
            case "datasetStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_DATASETS;
            case "namedGraphStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_NAMED_GRAPHS;
            case "timestampedNamedGraphStream" -> LogicalStreamType.LOGICAL_STREAM_TYPE_TIMESTAMPED_NAMED_GRAPHS;
            default -> null;
        };
    }

    public static <TNode, TDatatype> List<TNode> getRdfStaxAnnotation(
        ProtoDecoderConverter<TNode, TDatatype> converter,
        LogicalStreamType logicalType,
        TNode subjectNode
    ) {
        var typeIri = getRdfStaxType(logicalType);
        if (typeIri == null) {
            throw new IllegalArgumentException("Unsupported logical stream type: " + logicalType);
        }

        TNode bNode = converter.makeBlankNode(UUID.randomUUID().toString());
        return List.of(
            converter.makeTriple(subjectNode, converter.makeIriNode(STAX_PREFIX + "hasStreamTypeUsage"), bNode),
            converter.makeTriple(
                bNode,
                converter.makeIriNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                converter.makeIriNode(STAX_PREFIX + "RdfStreamTypeUsage")
            ),
            converter.makeTriple(
                bNode,
                converter.makeIriNode(STAX_PREFIX + "hasStreamType"),
                converter.makeIriNode(typeIri)
            )
        );
    }
}
