package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable.ListBuffer

/**
 * IRI and datatype encoder.
 * Maintains internal lookups for prefixes, names, and datatypes. Uses the LRU strategy for eviction.
 *
 * @param opt Jelly options
 */
private[core] final class NameEncoder(opt: RdfStreamOptions):
  private val nameLookup = new EncoderLookup(opt.maxNameTableSize)
  private val prefixLookup = new EncoderLookup(opt.maxPrefixTableSize)
  private val dtLookup = new EncoderLookup(opt.maxDatatypeTableSize)
  private val dtTable = new DecoderLookup[RdfLiteral.LiteralKind.Datatype](opt.maxDatatypeTableSize)

  /**
   * Try to extract the prefix out of the IRI.
   *
   * Somewhat based on [[org.apache.jena.riot.system.PrefixMapStd.getPossibleKey]]
   * @param iri IRI
   * @return prefix or null (micro-optimization, don't hit me)
   */
  private def getIriPrefix(iri: String): String =
    iri.lastIndexOf('#') match
      case i if i > -1 => iri.substring(0, i + 1)
      case _ =>
        iri.lastIndexOf('/') match
          case i if i > -1 => iri.substring(0, i + 1)
          case _ => null

  /**
   * Encodes an IRI to a protobuf representation.
   * Also adds the necessary prefix and name lookup entries to the buffer.
   * @param iri IRI to be encoded
   * @param rowsBuffer buffer to which the new lookup entries should be appended
   * @return protobuf representation of the IRI
   */
  def encodeIri(iri: String, rowsBuffer: ListBuffer[RdfStreamRow]): RdfIri =
    def plainIriEncode: RdfIri =
      nameLookup.addEntry(iri) match
        case EncoderValue(getId, _, false) =>
          RdfIri(nameId = getId)
        case EncoderValue(getId, setId, true) =>
          rowsBuffer.append(
            RdfStreamRow(RdfStreamRow.Row.Name(
              RdfNameEntry(id = setId, value = iri)
            ))
          )
          RdfIri(nameId = getId)

    if opt.maxPrefixTableSize == 0 then
      // Use a lighter algorithm if the prefix table is disabled
      return plainIriEncode

    getIriPrefix(iri) match
      case null => plainIriEncode
      case prefix =>
        val postfix = iri.substring(prefix.length)
        val pVal = prefixLookup.addEntry(prefix)
        val iVal = if postfix.nonEmpty then nameLookup.addEntry(postfix) else EncoderValue.Empty

        if pVal.newEntry then rowsBuffer.append(
          RdfStreamRow(RdfStreamRow.Row.Prefix(
            RdfPrefixEntry(pVal.setId, prefix)
          ))
        )
        if iVal.newEntry then rowsBuffer.append(
          RdfStreamRow(RdfStreamRow.Row.Name(
            RdfNameEntry(iVal.setId, postfix)
          ))
        )
        RdfIri(prefixId = pVal.getId, nameId = iVal.getId)

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
