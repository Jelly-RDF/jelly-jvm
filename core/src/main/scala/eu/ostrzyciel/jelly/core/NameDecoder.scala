package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

private[core] final class NameDecoder(opt: RdfStreamOptions):
  private val prefixLookup = new DecoderLookup[String](opt.maxPrefixTableSize)
  private val nameLookup = new DecoderLookup[String](opt.maxNameTableSize)

  /**
   * Update the name table.
   * @param nameRow name row
   * @throws ArrayIndexOutOfBoundsException if id < 1 or id > maxNameTableSize
   */
  inline def updateNames(nameRow: RdfNameEntry): Unit =
    nameLookup.update(nameRow.id, nameRow.value)

  /**
   * Update the prefix table.
   * @param prefixRow prefix row
   * @throws ArrayIndexOutOfBoundsException if id < 1 or id > maxPrefixTableSize
   */
  inline def updatePrefixes(prefixRow: RdfPrefixEntry): Unit =
    prefixLookup.update(prefixRow.id, prefixRow.value)

  /**
   * Reconstruct an IRI from its prefix and name ids.
   * @param iri IRI from protobuf
   * @return full IRI combining the prefix and the name
   * @throws ArrayIndexOutOfBoundsException if [[iri]] had indices out of lookup table bounds
   * @throws MissingPrefixEntryError if the prefix is not set in the table
   * @throws MissingNameEntryError if the name is not set in the table
   */
  def decode(iri: RdfIri): String =
    val prefix = iri.prefixId match
      case 0 => ""
      case id =>
        val p = prefixLookup.get(id)
        if p == null then throw MissingPrefixEntryError(id)
        p
    val name = iri.nameId match
      case 0 => ""
      case id =>
        val n = nameLookup.get(id)
        if n == null then throw MissingNameEntryError(id)
        n

    prefix + name
