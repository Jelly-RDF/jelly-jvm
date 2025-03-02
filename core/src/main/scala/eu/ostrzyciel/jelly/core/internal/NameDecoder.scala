package eu.ostrzyciel.jelly.core.internal

import eu.ostrzyciel.jelly.core.proto.v1.*

object NameDecoder:
  /**
   * Create a new NameDecoder.
   * @param prefixTableSize size of the prefix table
   * @param nameTableSize size of the name table
   * @param iriFactory factory for creating IRIs
   * @tparam TIri type of the IRI
   * @return new NameDecoder
   */
  def apply[TIri](
    prefixTableSize: Int, nameTableSize: Int, iriFactory: java.util.function.Function[String, TIri]
  ): NameDecoder[TIri] =
    new NameDecoderImpl(prefixTableSize, nameTableSize, iriFactory)

/**
 * Interface for NameDecoder exposed for Jelly extensions.
 * @tparam TIri type of the IRI
 */
private[core] trait NameDecoder[TIri]:
  /**
   * Update the name table with a new entry.
   * @param nameEntry new name entry
   */
  def updateNames(nameEntry: RdfNameEntry): Unit

  /**
   * Update the prefix table with a new entry.
   * @param prefixEntry new prefix entry
   */
  def updatePrefixes(prefixEntry: RdfPrefixEntry): Unit

  /**
   * Reconstruct an IRI from its prefix and name ids.
   * @param iri IRI row from the Jelly proto
   * @return full IRI combining the prefix and the name
   */
  def decode(iri: RdfIri): TIri
