package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions

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
