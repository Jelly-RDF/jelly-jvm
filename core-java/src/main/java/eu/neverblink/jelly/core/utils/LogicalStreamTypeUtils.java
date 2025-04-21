package eu.neverblink.jelly.core.utils;

import eu.neverblink.jelly.core.ProtoDecoderConverter;
import eu.neverblink.jelly.core.proto.v1.LogicalStreamType;
import java.util.List;
import java.util.UUID;

public class LogicalStreamTypeUtils {

    private static final String STAX_PREFIX = "https://w3id.org/stax/ontology#";

    private LogicalStreamTypeUtils() {}

    /**
     * Converts the logical stream type to its base concrete stream type in RDF-STaX.
     * For example, LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS will be converted to LogicalStreamType.DATASETS.
     * UNSPECIFIED values will be left as-is.
     *
     * @param logicalType logical stream type
     * @return base stream type
     */
    public static LogicalStreamType toBaseType(LogicalStreamType logicalType) {
        return LogicalStreamType.forNumber(logicalType.getNumber() % 10);
    }

    /**
     * Checks if the logical stream type is equal to or a subtype of the other logical stream type.
     * For example, LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS is a subtype of LogicalStreamType.DATASETS.
     *
     * @param logicalType the logical stream type to check
     * @param other the other logical stream type
     * @return true if the logical stream type is equal to or a subtype of the other logical stream type
     */
    public static boolean isEqualOrSubtypeOf(LogicalStreamType logicalType, LogicalStreamType other) {
        return (
            logicalType.equals(other) ||
            String.valueOf(logicalType.getNumber()).endsWith(String.valueOf(other.getNumber()))
        );
    }

    /**
     * Returns the IRI of the RDF-STaX stream type individual for the logical stream type.
     * If the logical stream type is not supported or is not specified, None is returned.
     *
     * @param logicalType the logical stream type
     * @return the IRI of the RDF-STaX stream type individual
     */
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

    /**
     * Creates a logical stream type from an RDF-STaX stream type individual IRI.
     *
     * @param iri the IRI of the RDF-STaX stream type individual
     * @return the logical stream type, or None if the IRI is not a valid RDF-STaX stream type individual
     */
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

    /**
     * Returns an RDF-STaX annotation for the logical stream type, in RDF. The annotation simply states that
     * <subjectNode> has a stream type usage, and that stream type usage has this stream type.
     * <p>
     * Example in Turtle for a flat triple stream:
     * <subjectNode> stax:hasStreamTypeUsage [
     *     a stax:RdfStreamTypeUsage ;
     *     stax:hasStreamType stax:flatTripleStream
     * ] .
     *
     * @param logicalType the logical stream type
     * @param subjectNode the subject node to annotate
     * @param converter the converter to use for creating RDF nodes and triples
     * @param <TNode> the type of RDF nodes
     * @param <TDatatype> the type of RDF triples
     * @throws IllegalArgumentException if the logical stream type is not supported
     * @return the RDF-STaX annotation
     */
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
