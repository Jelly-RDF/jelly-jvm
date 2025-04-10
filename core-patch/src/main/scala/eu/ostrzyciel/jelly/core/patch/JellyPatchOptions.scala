package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoDeserializationError
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.experimental

/**
 * Utilities for working with RdfPatchOptions.
 */
@experimental
object JellyPatchOptions:
  /**
   * Convert a Jelly RdfStreamOptions to a Jelly Patch RdfPatchOptions.
   *
   * GRAPHS physical type is converted to QUADS. Logical stream type and other fields that are not
   * relevant to RDF Patch are ignored.
   *
   * @param opt RdfStreamOptions
   * @return RdfPatchOptions
   */
  def fromJellyOptions(opt: RdfStreamOptions): RdfPatchOptions =
    fromBaseOptions(opt).withStatementType(fromJellyPhysicalType(opt.physicalType))

  /**
   * Convert a BaseJellyOptions instance to a Jelly Patch RdfPatchOptions.
   * @param opt BaseJellyOptions
   * @return RdfPatchOptions
   */
  def fromBaseOptions(opt: BaseJellyOptions): RdfPatchOptions =
    RdfPatchOptions(
      generalizedStatements = opt.generalizedStatements,
      rdfStar = opt.rdfStar,
      maxNameTableSize = opt.maxNameTableSize,
      maxPrefixTableSize = opt.maxPrefixTableSize,
      maxDatatypeTableSize = opt.maxDatatypeTableSize,
      version = PatchConstants.protoVersion,
    )

  /**
   * Checks if the requested stream options are supported. Throws an exception if not.
   *
   * The usage and motivation is analogous to [[JellyOptions.checkCompatibility]].
   *
   * We check:
   * - version (must be <= PatchConstants.protoVersion and <= supportedOptions.version)
   * - stream type (must be == supportedOptions.streamType if set)
   * - generalized statements (must be < supportedOptions.generalizedStatements)
   * - RDF star (must be <= supportedOptions.rdfStar)
   * - max name table size (must be <= supportedOptions.maxNameTableSize and >= 16).
   * - max prefix table size (must be <= supportedOptions.maxPrefixTableSize)
   * - max datatype table size (must be <= supportedOptions.maxDatatypeTableSize and >= 8)
   *
   * We don't check:
   * - statement type (this is done by the implementations of PatchDecoderImpl)
   *
   * @param requestedOptions Requested options of the stream.
   * @param supportedOptions Options that can be safely supported.
   * @throws RdfProtoDeserializationError on validation error
   */
  def checkCompatibility(requestedOptions: RdfPatchOptions, supportedOptions: RdfPatchOptions): Unit =
    BaseJellyOptions.checkCompatibility(requestedOptions, supportedOptions, PatchConstants.protoVersion)
    if requestedOptions.streamType.isUnspecified then
      throw new RdfProtoDeserializationError("The patch stream type is unspecified. " +
        "The stream_type field is required and must be set to a valid value.")
    if !supportedOptions.streamType.isUnspecified && supportedOptions.streamType != requestedOptions.streamType then
      throw new RdfProtoDeserializationError(
        s"The requested stream type ${requestedOptions.streamType} is not supported. " +
          s"Only ${supportedOptions.streamType} is supported.")

  /**
   * Convert a Jelly-RDF physical type to a Jelly-Patch physical type.
   *
   * GRAPHS physical type is converted to QUADS.
   *
   * @param t PhysicalStreamType
   * @return PatchStatementType
   */
  def fromJellyPhysicalType(t: PhysicalStreamType): PatchStatementType = t match
    case PhysicalStreamType.TRIPLES => PatchStatementType.TRIPLES
    case PhysicalStreamType.QUADS => PatchStatementType.QUADS
    case PhysicalStreamType.GRAPHS => PatchStatementType.QUADS
    case _ => PatchStatementType.UNSPECIFIED

  /** See: [[JellyOptions.bigStrict]] */
  lazy val bigStrict: RdfPatchOptions = fromJellyOptions(JellyOptions.bigStrict)

  /** See: [[JellyOptions.bigGeneralized]] */
  lazy val bigGeneralized: RdfPatchOptions = fromJellyOptions(JellyOptions.bigGeneralized)

  /** See: [[JellyOptions.bigRdfStar]] */
  lazy val bigRdfStar: RdfPatchOptions = fromJellyOptions(JellyOptions.bigRdfStar)

  /** See: [[JellyOptions.bigAllFeatures]] */
  lazy val bigAllFeatures: RdfPatchOptions = fromJellyOptions(JellyOptions.bigAllFeatures)

  /** See: [[JellyOptions.smallStrict]] */
  lazy val smallStrict: RdfPatchOptions = fromJellyOptions(JellyOptions.smallStrict)

  /** See: [[JellyOptions.smallGeneralized]] */
  lazy val smallGeneralized: RdfPatchOptions = fromJellyOptions(JellyOptions.smallGeneralized)

  /** See: [[JellyOptions.smallRdfStar]] */
  lazy val smallRdfStar: RdfPatchOptions = fromJellyOptions(JellyOptions.smallRdfStar)

  /** See: [[JellyOptions.smallAllFeatures]] */
  lazy val smallAllFeatures: RdfPatchOptions = fromJellyOptions(JellyOptions.smallAllFeatures)

  /**
   * Default maximum supported options for Jelly-Patch decoders.
   *
   * By default, Jelly-JVM will refuse to read streams that exceed these limits (e.g., with a name
   * lookup table larger than 4096 entries).
   */
  lazy val defaultSupportedOptions: RdfPatchOptions = fromJellyOptions(JellyOptions.defaultSupportedOptions)
