package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.{LogicalStreamType, PhysicalStreamType, RdfStreamOptions}

/**
 * A collection of convenient streaming option presets.
 * None of the presets specifies the stream type – do that with the .withPhysicalType method.
 */
object JellyOptions:

  /**
   * "Big" preset suitable for high-volume streams and larger machines.
   * Does not allow generalized RDF statements.
   * @return
   */
  def bigStrict: RdfStreamOptions = RdfStreamOptions(
    maxNameTableSize = 4000,
    maxPrefixTableSize = 150,
    maxDatatypeTableSize = 32,
  )

  /**
   * "Big" preset suitable for high-volume streams and larger machines.
   * Allows generalized RDF statements.
   * @return
   */
  def bigGeneralized: RdfStreamOptions =
    bigStrict.withGeneralizedStatements(true)

  /**
   * "Big" preset suitable for high-volume streams and larger machines.
   * Allows RDF-star statements.
   * @return
   */
  def bigRdfStar: RdfStreamOptions =
    bigStrict.withRdfStar(true)

  /**
   * "Small" preset suitable for low-volume streams and smaller machines.
   * Does not allow generalized RDF statements.
   * @return
   */
  def smallStrict: RdfStreamOptions = RdfStreamOptions(
    maxNameTableSize = 128,
    maxPrefixTableSize = 16,
    maxDatatypeTableSize = 16,
  )

  /**
   * "Small" preset suitable for low-volume streams and smaller machines.
   * Allows generalized RDF statements.
   * @return
   */
  def smallGeneralized: RdfStreamOptions =
    smallStrict.withGeneralizedStatements(true)
    
  /**
    * "Small" preset suitable for low-volume streams and smaller machines.
    * Allows RDF-star statements.
    * @return
    */
  def smallRdfStar: RdfStreamOptions =
    smallStrict.withRdfStar(true)

  /**
   * Default maximum supported options for Jelly decoders.
   * 
   * This means that by default Jelly-JVM will refuse to read streams that exceed these limits (e.g., with a
   * name lookup table larger than 4096 entries).
   * 
   * To change these defaults, you should pass a different RdfStreamOptions object to the decoder.
   * You should use this method to get the default options and then modify them as needed.
   * For example, to disable RDF-star support, you can do this:
   * <code>
   * val myOptions = JellyOptions.defaultSupportedOptions.withRdfStar(false)
   * </code>
   * 
   * If you were to pass a default RdfStreamOptions object to the decoder, it would simply refuse to read any stream
   * as (by default) it will have all max table sizes set to 0. So, you should always use this method as the base.
   * 
   * @return
   */
  def defaultSupportedOptions: RdfStreamOptions = RdfStreamOptions(
    generalizedStatements = true,
    rdfStar = true,
    maxNameTableSize = 4096,
    maxPrefixTableSize = 1024,
    maxDatatypeTableSize = 256,
    version = Constants.protoVersion,
  )

  /**
   * Checks if the requested stream options are supported. Throws an exception if not.
   *
   * This is used in two places:
   * - By [[eu.ostrzyciel.jelly.core.ProtoDecoder]] implementations to check if it's safe to decode the stream
   *   This MUST be called before any data (besides the stream options) is ingested. Otherwise, the options may
   *   request something dangerous, like allocating a very large lookup table, which could be used to perform a
   *   denial-of-service attack.
   * - By implementations the gRPC streaming service from the jelly-grpc module to check if the client is
   *   requesting stream options that the server can support.
   *
   * We check:
   * - version (must be <= Constants.protoVersion and <= supportedOptions.version)
   * - generalized statements (must be <= supportedOptions.generalizedStatements)
   * - RDF star (must be <= supportedOptions.rdfStar)
   * - max name table size (must be <= supportedOptions.maxNameTableSize and >= 16).
   * - max prefix table size (must be <= supportedOptions.maxPrefixTableSize)
   * - max datatype table size (must be <= supportedOptions.maxDatatypeTableSize and >= 8)
   * - logical stream type (must be compatible with physical stream type and compatible with expected log. stream type)
   *
   * We don't check:
   * - physical stream type (this is done by the implementations of ProtoDecoderImpl)
   * - stream name (we don't care about it)
   *
   * See also the stream options handling table in the gRPC spec:
   * https://jelly-rdf.github.io/dev/specification/streaming/#stream-options-handling
   * This is not exactly what we are doing here (the table is about client-server interactions), but it's a good
   * reference for the logic used here.
   *
   * @param requestedOptions Requested options of the stream.
   * @param supportedOptions Options that can be safely supported.
   */
  def checkCompatibility(requestedOptions: RdfStreamOptions, supportedOptions: RdfStreamOptions): Unit =
    if requestedOptions.version > supportedOptions.version || requestedOptions.version > Constants.protoVersion then
      throw new RdfProtoDeserializationError(s"Unsupported proto version: ${requestedOptions.version}. " +
        s"Was expecting at most version ${supportedOptions.version}. " +
        s"This library version supports up to version ${Constants.protoVersion}.")

    if requestedOptions.generalizedStatements && !supportedOptions.generalizedStatements then
      throw new RdfProtoDeserializationError(s"The stream uses generalized statements, which are not supported. " +
        s"Either disable generalized statements or enable them in the supportedOptions.")

    if requestedOptions.rdfStar && !supportedOptions.rdfStar then
      throw new RdfProtoDeserializationError(s"The stream uses RDF-star, which is not supported. Either disable" +
        s" RDF-star or enable it in the supportedOptions.")

    def checkTableSize(name: String, size: Int, supportedSize: Int, minSize: Int = 0): Unit =
      if size > supportedSize then
        throw new RdfProtoDeserializationError(s"The stream uses a ${name.toLowerCase} table size of $size, which is " +
          s"larger than the maximum supported size of $supportedSize."
        )
      if size < minSize then
        throw new RdfProtoDeserializationError(s"The stream uses a ${name.toLowerCase} table size of $size, which is " +
          s"smaller than the minimum supported size of $minSize."
        )

    // The minimum sizes are hard-coded because it would be impossible to reliably encode the stream
    // with smaller tables, especially if RDF-star is used.
    checkTableSize("Name", requestedOptions.maxNameTableSize, supportedOptions.maxNameTableSize, 16)
    checkTableSize("Prefix", requestedOptions.maxPrefixTableSize, supportedOptions.maxPrefixTableSize)
    checkTableSize("Datatype", requestedOptions.maxDatatypeTableSize, supportedOptions.maxDatatypeTableSize, 8)

    checkLogicalStreamType(requestedOptions, supportedOptions.logicalType)

  /**
   * Checks if the logical and physical stream types are compatible. Additionally, if the expected logical stream type
   * is provided, checks if the actual logical stream type is a subtype of the expected one.
   *
   * @param options        Options of the stream.
   * @param expLogicalType Expected logical stream type. If UNSPECIFIED, no check is performed.
   */
  private def checkLogicalStreamType(options: RdfStreamOptions, expLogicalType: LogicalStreamType): Unit =
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
