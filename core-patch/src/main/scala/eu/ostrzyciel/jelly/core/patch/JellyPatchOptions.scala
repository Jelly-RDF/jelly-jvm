package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.{BaseJellyOptions, PhysicalStreamType, RdfStreamOptions}
import eu.ostrzyciel.jelly.core.proto.v1.patch.{PatchPhysicalType, RdfPatchOptions}

/**
 * Utilities for working with RdfPatchOptions.
 */
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
    fromBaseOptions(opt).withPhysicalType(fromJellyPhysicalType(opt.physicalType))

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
   * - generalized statements (must be <= supportedOptions.generalizedStatements)
   * - RDF star (must be <= supportedOptions.rdfStar)
   * - max name table size (must be <= supportedOptions.maxNameTableSize and >= 16).
   * - max prefix table size (must be <= supportedOptions.maxPrefixTableSize)
   * - max datatype table size (must be <= supportedOptions.maxDatatypeTableSize and >= 8)
   *
   * We don't check:
   * - physical stream type (this is done by the implementations of PatchDecoderImpl)
   *
   * @param requestedOptions Requested options of the stream.
   * @param supportedOptions Options that can be safely supported.
   * @throws RdfProtoDeserializationError on validation error
   */
  def checkCompatibility(requestedOptions: RdfPatchOptions, supportedOptions: RdfPatchOptions): Unit =
    BaseJellyOptions.checkCompatibility(requestedOptions, supportedOptions, PatchConstants.protoVersion)

  /**
   * Convert a Jelly-RDF physical type to a Jelly-Patch physical type.
   *
   * GRAPHS physical type is converted to QUADS.
   *
   * @param t PhysicalStreamType
   * @return PatchPhysicalType
   */
  def fromJellyPhysicalType(t: PhysicalStreamType): PatchPhysicalType = t match
    case PhysicalStreamType.TRIPLES => PatchPhysicalType.TRIPLES
    case PhysicalStreamType.QUADS => PatchPhysicalType.QUADS
    case PhysicalStreamType.GRAPHS => PatchPhysicalType.QUADS
    case _ => PatchPhysicalType.UNSPECIFIED

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
