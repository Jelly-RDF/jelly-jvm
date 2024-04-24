package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.LogicalStreamType

import java.util.UUID

private trait LogicalStreamTypeExtensions:
  val staxPrefix = "https://w3id.org/stax/ontology#"

  extension (logicalType: LogicalStreamType)
    /**
     * Converts the logical stream type to its base concrete stream type in RDF-STaX.
     * For example, [[LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS]] will be converted to [[LogicalStreamType.DATASETS]].
     * UNSPECIFIED values will be left as-is.
     *
     * @return base stream type
     */
    def toBaseType: LogicalStreamType =
      LogicalStreamType.fromValue(logicalType.value % 10)

    /**
     * Checks if the logical stream type is equal to or a subtype of the other logical stream type.
     * For example, [[LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS]] is a subtype of [[LogicalStreamType.DATASETS]].
     *
     * @param other the other logical stream type
     * @return true if the logical stream type is equal to or a subtype of the other logical stream type
     */
    def isEqualOrSubtypeOf(other: LogicalStreamType): Boolean =
      logicalType == other || logicalType.value.toString.endsWith(other.value.toString)

    /**
     * Returns the IRI of the RDF-STaX stream type individual for the logical stream type.
     * If the logical stream type is not supported or is not specified, None is returned.
     *
     * @return the IRI of the RDF-STaX stream type individual
     */
    def getRdfStaxType: Option[String] =
      logicalType match
        case LogicalStreamType.FLAT_TRIPLES => Some(s"${staxPrefix}flatTripleStream")
        case LogicalStreamType.FLAT_QUADS => Some(s"${staxPrefix}flatQuadStream")
        case LogicalStreamType.GRAPHS => Some(s"${staxPrefix}graphStream")
        case LogicalStreamType.SUBJECT_GRAPHS => Some(s"${staxPrefix}subjectGraphStream")
        case LogicalStreamType.DATASETS => Some(s"${staxPrefix}datasetStream")
        case LogicalStreamType.NAMED_GRAPHS => Some(s"${staxPrefix}namedGraphStream")
        case LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS => Some(s"${staxPrefix}timestampedNamedGraphStream")
        case _ => None

    /**
     * Returns an RDF-STaX annotation for the logical stream type, in RDF. The annotation simply states that
     * <subjectNode> has a stream type usage, and that stream type usage has this stream type.
     *
     * Example in Turtle for a flat triple stream:
     * <subjectNode> stax:hasStreamTypeUsage [
     *     a stax:RdfStreamTypeUsage ;
     *     stax:hasStreamType stax:flatTripleStream
     * ] .
     *
     * @param subjectNode the subject node to annotate
     * @param converterFactory the converter factory to use for creating RDF nodes and triples
     * @tparam TNode the type of RDF nodes
     * @tparam TTriple the type of RDF triples
     * @throws IllegalArgumentException if the logical stream type is not supported
     * @return the RDF-STaX annotation
     */
    def getRdfStaxAnnotation[TNode, TTriple](subjectNode: TNode)
      (using converterFactory: ConverterFactory[?, ?, TNode, ?, TTriple, ?]): Seq[TTriple] =
      getRdfStaxType match
        case Some(typeIri) =>
          val converter = converterFactory.decoderConverter
          val bNode = converter.makeBlankNode(UUID.randomUUID().toString)
          Seq(
            converter.makeTriple(
              subjectNode,
              converter.makeIriNode(s"${staxPrefix}hasStreamTypeUsage"),
              bNode
            ),
            converter.makeTriple(
              bNode,
              converter.makeIriNode("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
              converter.makeIriNode(s"${staxPrefix}RdfStreamTypeUsage")
            ),
            converter.makeTriple(
              bNode,
              converter.makeIriNode(s"${staxPrefix}hasStreamType"),
              converter.makeIriNode(typeIri)
            )
        )
        case None => throw new IllegalArgumentException(s"Unsupported logical stream type: $logicalType")

private object LogicalStreamTypeExtensions extends LogicalStreamTypeExtensions

export LogicalStreamTypeExtensions.*

object LogicalStreamTypeFactory:

  /**
   * Creates a logical stream type from an RDF-STaX stream type individual IRI.
   *
   * @param iri the IRI of the RDF-STaX stream type individual
   * @return the logical stream type, or None if the IRI is not a valid RDF-STaX stream type individual
   */
  def fromOntologyIri(iri: String): Option[LogicalStreamType] =
    if !iri.startsWith(staxPrefix) then
      return None

    iri.substring(staxPrefix.length) match
      case "flatTripleStream" => Some(LogicalStreamType.FLAT_TRIPLES)
      case "flatQuadStream" => Some(LogicalStreamType.FLAT_QUADS)
      case "graphStream" => Some(LogicalStreamType.GRAPHS)
      case "subjectGraphStream" => Some(LogicalStreamType.SUBJECT_GRAPHS)
      case "datasetStream" => Some(LogicalStreamType.DATASETS)
      case "namedGraphStream" => Some(LogicalStreamType.NAMED_GRAPHS)
      case "timestampedNamedGraphStream" => Some(LogicalStreamType.TIMESTAMPED_NAMED_GRAPHS)
      case _ => None
