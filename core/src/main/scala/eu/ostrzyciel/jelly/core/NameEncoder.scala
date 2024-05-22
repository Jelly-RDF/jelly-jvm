package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable.ListBuffer

private[core] object NameEncoder:
  private val repeatDatatype = RdfLiteral.LiteralKind.Datatype(0)

/**
 * IRI and datatype encoder.
 * Maintains internal lookups for prefixes, names, and datatypes. Uses the LRU strategy for eviction.
 *
 * @param opt Jelly options
 */
private[core] final class NameEncoder(opt: RdfStreamOptions):
  import NameEncoder.*

  private val nameLookup = new EncoderLookup(opt.maxNameTableSize)
  private val prefixLookup = new EncoderLookup(opt.maxPrefixTableSize)
  private val dtLookup = new EncoderLookup(opt.maxDatatypeTableSize)
  private val dtTable = new DecoderLookup[RdfLiteral.LiteralKind.Datatype](opt.maxDatatypeTableSize)

  private var lastIriPrefixId: Int = -1000
  private var lastIriNameId: Int = 0

  /**
   * Try to extract the prefix out of the IRI.
   *
   * Somewhat based on [[org.apache.jena.riot.system.PrefixMapStd.getPossibleKey]]
   * @param iri IRI
   * @return prefix which can be empty, never null
   */
  private def getIriPrefix(iri: String): String =
    iri.lastIndexOf('#') match
      case i if i > -1 => iri.substring(0, i + 1)
      case _ =>
        iri.lastIndexOf('/') match
          case i if i > -1 => iri.substring(0, i + 1)
          case _ => ""

  /**
   * Obtain the id for the name lookup table to be communicated to the consumer.
   * This method checks if new id = last_id + 1, and if so, it returns 0.
   *
   * @param getId the getId from the EncoderLookup
   * @return the id to be communicated to the consumer
   */
  private inline def getNameIdWithRepeat(getId: Int): Int =
    if lastIriNameId + 1 == getId then
      // If the last node had id - 1, we can tell it to the consumer in a shorthand manner
      lastIriNameId = getId
      0
    else
      lastIriNameId = getId
      getId

  /**
   * Encodes an IRI to a protobuf representation.
   * Also adds the necessary prefix and name lookup entries to the buffer.
   * @param iri IRI to be encoded
   * @param rowsBuffer buffer to which the new lookup entries should be appended
   * @return protobuf representation of the IRI
   */
  def encodeIri(iri: String, rowsBuffer: ListBuffer[RdfStreamRow]): RdfIri =
    if opt.maxPrefixTableSize == 0 then
      // Use a lighter algorithm if the prefix table is disabled
      val nameLookupEntry = nameLookup.addEntry(iri)
      if nameLookupEntry.newEntry then
        rowsBuffer.append(
          RdfStreamRow(RdfStreamRow.Row.Name(
            RdfNameEntry(id = nameLookupEntry.setId, value = iri)
          ))
        )
      // We set the prefixId to 0, but it's a special case, because the prefix table is disabled.
      // The consumer will interpret this as no prefix.
      RdfIri(nameId = getNameIdWithRepeat(nameLookupEntry.getId))
    else
      val prefix = getIriPrefix(iri)
      val postfix = iri.substring(prefix.length)
      val prefixLookupEntry = prefixLookup.addEntry(prefix)
      val nameLookupEntry = nameLookup.addEntry(postfix)

      if prefixLookupEntry.newEntry then rowsBuffer.append(
        RdfStreamRow(RdfStreamRow.Row.Prefix(
          RdfPrefixEntry(prefixLookupEntry.setId, prefix)
        ))
      )
      if nameLookupEntry.newEntry then rowsBuffer.append(
        RdfStreamRow(RdfStreamRow.Row.Name(
          RdfNameEntry(nameLookupEntry.setId, postfix)
        ))
      )

      val nameIdWithRepeat = getNameIdWithRepeat(nameLookupEntry.getId)
      if lastIriPrefixId == prefixLookupEntry.getId then
        // If the last IRI had the same prefix, we can tell the consumer to reuse it.
        // prefixId = 0 by default in this constructor.
        // No need to update lastIriPrefixId, because it's the same.
        RdfIri(nameId = nameIdWithRepeat)
      else
        lastIriPrefixId = prefixLookupEntry.getId
        RdfIri(
          prefixId = prefixLookupEntry.getId,
          nameId = nameIdWithRepeat
        )

  /**
   * Encodes a datatype IRI to a protobuf representation.
   * Also adds the necessary datatype lookup entries to the buffer.
   * @param dtIri datatype IRI
   * @param rowsBuffer buffer to which the new lookup entries should be appended
   * @return datatype representation for a literal
   */
  def encodeDatatype(dtIri: String, rowsBuffer: ListBuffer[RdfStreamRow]): RdfLiteral.LiteralKind.Datatype =
    val dtVal = dtLookup.addEntry(dtIri)
    if dtVal.newEntry then
      val datatype = RdfLiteral.LiteralKind.Datatype(dtVal.getId)
      dtTable.update(
        dtVal.getId,
        datatype
      )
      rowsBuffer.append(
        RdfStreamRow(RdfStreamRow.Row.Datatype(
          RdfDatatypeEntry(id = dtVal.setId, value = dtIri)
        ))
      )
      datatype
    else
      dtTable.get(dtVal.getId)
