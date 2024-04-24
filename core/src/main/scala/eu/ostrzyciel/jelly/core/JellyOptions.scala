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
    useRepeat = true,
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
    useRepeat = true,
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
