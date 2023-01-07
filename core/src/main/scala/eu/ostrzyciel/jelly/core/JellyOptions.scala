package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.RdfStreamOptions

/**
 * A collection of convenient streaming option presets.
 * None of the presets specifies the stream type – do that with the .withStreamType method.
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
