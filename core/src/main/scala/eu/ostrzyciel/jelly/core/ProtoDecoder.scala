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
   * Checks if the stream options are supported by the decoder.
   * Throws an exception if not.
   * 
   * This MUST be called before any data (besides the stream options) is ingested. Otherwise, the options may
   * request something dangerous, like allocating a very large lookup table, which could be used to perform a
   * denial-of-service attack.
   * 
   * We check:
   * - version (must be <= Constants.protoVersion and <= supportedOptions.version)
   * - generalized statements (must be <= supportedOptions.generalizedStatements)
   * - RDF star (must be <= supportedOptions.rdfStar)
   * - max name table size (must be <= supportedOptions.maxNameTableSize).
   * - max prefix table size (must be <= supportedOptions.maxPrefixTableSize)
   * - max datatype table size (must be <= supportedOptions.maxDatatypeTableSize)
   * - logical stream type (must be compatible with physical stream type and compatible with expected log. stream type)
   * 
   * We don't check:
   * - physical stream type (this is done by the implementations of ProtoDecoderImpl)
   * - stream name (we don't care about it)
   * 
   * See also the stream options handling table in the gRPC spec: 
   * https://jelly-rdf.github.io/1.0/specification/streaming/#stream-options-handling
   * This is not exactly what we are doing here (the table is about client-server interactions), but it's a good
   * reference for the logic used here.
   * 
   * @param options Options of the stream.
   * @param supportedOptions Options supported by the decoder.
   */
  protected final def checkOptions(options: RdfStreamOptions, supportedOptions: RdfStreamOptions): Unit =
    if options.version > supportedOptions.version || options.version > Constants.protoVersion then
      throw new RdfProtoDeserializationError(s"Unsupported proto version: ${options.version}. " +
        s"Was expecting at most version ${supportedOptions.version}. " +
        s"This library version supports up to version ${Constants.protoVersion}.")
      
    if options.generalizedStatements && !supportedOptions.generalizedStatements then
      throw new RdfProtoDeserializationError(s"The stream uses generalized statements, which the user marked as not " + 
        s"supported. To read this stream, set generalizedStatements to true in the supportedOptions for this decoder.")
      
    if options.rdfStar && !supportedOptions.rdfStar then
      throw new RdfProtoDeserializationError(s"The stream uses RDF-star, which the user marked as not supported. " +
        s"To read this stream, set rdfStar to true in the supportedOptions for this decoder.")
      
    def checkTableSize(name: String, size: Int, supportedSize: Int): Unit =
      if size > supportedSize then
        throw new RdfProtoDeserializationError(s"The stream uses a ${name.toLowerCase} table size of $size, which is " +
          s"larger than the maximum supported size of $supportedSize. To read this stream, set max${name}TableSize " +
          s"to at least $size in the supportedOptions for this decoder."
        )
      
    checkTableSize("Name", options.maxNameTableSize, supportedOptions.maxNameTableSize)
    checkTableSize("Prefix", options.maxPrefixTableSize, supportedOptions.maxPrefixTableSize)
    checkTableSize("Datatype", options.maxDatatypeTableSize, supportedOptions.maxDatatypeTableSize)
      
    checkLogicalStreamType(options, supportedOptions.logicalType)


  /**
   * Checks if the logical and physical stream types are compatible. Additionally, if the expected logical stream type
   * is provided, checks if the actual logical stream type is a subtype of the expected one.
   * @param options Options of the stream.
   * @param expLogicalType Expected logical stream type. If UNSPECIFIED, no check is performed.
   */
  private def checkLogicalStreamType(options: RdfStreamOptions, expLogicalType: LogicalStreamType):
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
      case LogicalStreamType.UNSPECIFIED => ()
      case v =>
        if !options.logicalType.isEqualOrSubtypeOf(v) then
          throw new RdfProtoDeserializationError(s"Expected logical stream type $v, got ${options.logicalType}. " +
            s"${options.logicalType} is not a subtype of $v.")
