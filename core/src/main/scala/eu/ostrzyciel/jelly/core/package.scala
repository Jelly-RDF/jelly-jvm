package eu.ostrzyciel.jelly

package object core:
  sealed class RdfProtoDeserializationError(msg: String) extends Error(msg)
  final class MissingPrefixEntryError(val prefixId: Int) extends RdfProtoDeserializationError(
    s"Missing entry in prefix table at ID: $prefixId"
  )
  final class MissingNameEntryError(val nameId: Int) extends RdfProtoDeserializationError(
    s"Missing entry in name table at ID: $nameId"
  )

  final class RdfProtoSerializationError(msg: String) extends Error(msg)

  // Constants
  object Constants:
    val jellyName = "Jelly"
    val jellyFileExtension = "jelly"
    val jellyContentType = "application/x-jelly-rdf"
