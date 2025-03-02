package eu.ostrzyciel.jelly.core.proto.v1

import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoDeserializationError

/**
 * Base options shared by Jelly-RDF, Jelly-Patch and possible future extensions.
 */
trait BaseJellyOptions:
  val generalizedStatements: Boolean
  val rdfStar: Boolean
  val maxNameTableSize: Int
  val maxPrefixTableSize: Int
  val maxDatatypeTableSize: Int
  val version: Int

/**
 * Utilities for working with BaseJellyOptions.
 */
private[core] object BaseJellyOptions:
  /**
   * Check if the requested options are compatible with the supported options and the system.
   *
   * This is used as part of the checks in [[eu.ostrzyciel.jelly.core.JellyOptions]].
   *
   * @param requestedOptions requested options
   * @param supportedOptions supported options
   * @param systemSupportedVersion maximum supported version by the system
   * @throws RdfProtoDeserializationError on validation error
   */
  def checkCompatibility(
    requestedOptions: BaseJellyOptions,
    supportedOptions: BaseJellyOptions,
    systemSupportedVersion: Int,
  ): Unit =
    if requestedOptions.version > supportedOptions.version || requestedOptions.version > systemSupportedVersion then
      throw new RdfProtoDeserializationError(s"Unsupported proto version: ${requestedOptions.version}. " +
        s"Was expecting at most version ${supportedOptions.version}. " +
        s"This library version supports up to version $systemSupportedVersion.")

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
