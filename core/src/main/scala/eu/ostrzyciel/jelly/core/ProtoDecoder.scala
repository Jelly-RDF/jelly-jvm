package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

/**
 * Base extendable trait for decoders of protobuf RDF streams.
 * 
 * See the implementation in [[ProtoDecoderImpl]].
 * 
 * @tparam TOut Type of the output of the decoder.
 */
trait ProtoDecoder[+TOut]:
  def getStreamOpt: Option[RdfStreamOptions]
  
  def ingestRow(row: RdfStreamRow): Option[TOut]

  /**
   * Checks if the version of the stream is supported.
   * Throws an exception if not.
   * @param options Options of the stream.
   */
  protected final def checkVersion(options: RdfStreamOptions): Unit =
    if options.version > Constants.protoVersion then
      throw new RdfProtoDeserializationError(s"Unsupported proto version: ${options.version}")

  protected final def checkLogicalStreamType(options: RdfStreamOptions, expLogicalType: Option[LogicalStreamType]):
  Unit =
    val baseLogicalType = options.logicalType.toBaseType

    val conflict = baseLogicalType match
      case LogicalStreamType.UNSPECIFIED => false
      case LogicalStreamType.FLAT_TRIPLES => options.physicalType match
        case PhysicalStreamType.QUADS => true
        case PhysicalStreamType.GRAPHS => true
        case _ => false
      case LogicalStreamType.FLAT_QUADS => options.physicalType match
        case PhysicalStreamType.TRIPLES => true
        case _ => false
      case LogicalStreamType.GRAPHS => options.physicalType match
        case PhysicalStreamType.QUADS => true
        case PhysicalStreamType.GRAPHS => true
        case _ => false
      case LogicalStreamType.DATASETS => options.physicalType match
        case PhysicalStreamType.TRIPLES => true
        case _ => false
      case _ => false

    if conflict then
      throw new RdfProtoDeserializationError(s"Logical stream type $baseLogicalType is incompatible with " +
        s"physical stream type ${options.physicalType}.")

    expLogicalType match
      case Some(v) =>
        if baseLogicalType != v then
          throw new RdfProtoDeserializationError(s"Expected logical stream type $v, got ${options.logicalType}. " +
            s"${options.logicalType} is not a subtype of $v.")
      case None =>
