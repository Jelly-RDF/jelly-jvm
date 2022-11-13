package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.RDFProtobufDeserializationError
import pl.ostrzyciel.jelly.core.proto.*

private[core] final class NameDecoder(opt: StreamOptions):
  private val prefixLookup = new DecoderLookup[String](opt.maxPrefixTableSize)
  private val nameLookup = new DecoderLookup[String](opt.maxNameTableSize)

  inline def updateNames(nameRow: RdfNameEntry): Unit =
    nameLookup.update(nameRow.id, nameRow.value)

  inline def updatePrefixes(prefixRow: RdfPrefixEntry): Unit =
    prefixLookup.update(prefixRow.id, prefixRow.value)

  def decode(iri: RdfIri): String =
    val prefix = iri.prefixId match
      case 0 => ""
      case id => prefixLookup.get(id)
    val name = iri.nameId match
      case 0 => ""
      case id => nameLookup.get(id)

    prefix + name
