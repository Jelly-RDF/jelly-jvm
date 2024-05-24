package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

private[core] final class NameDecoder(opt: RdfStreamOptions):
  private val prefixLookup = new DecoderLookup[String](opt.maxPrefixTableSize)
  private val nameLookup = new DecoderLookup[String](opt.maxNameTableSize)

  private var lastIriPrefixId: Int = 0
  private var lastIriNameId: Int = 0

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
   * @throws NullPointerException if the name or prefix are not found in the lookup table
   */
  def decode(iri: RdfIri): String =
    val prefix = iri.prefixId match
      case 0 if lastIriPrefixId < 1 => ""
      case 0 => prefixLookup.get(lastIriPrefixId)
      case id =>
        lastIriPrefixId = id
        prefixLookup.get(id)
        
    val name = iri.nameId match
      case 0 => 
        lastIriNameId += 1
        nameLookup.get(lastIriNameId)
      case id =>
        lastIriNameId = id
        nameLookup.get(id)

    prefix.concat(name)
