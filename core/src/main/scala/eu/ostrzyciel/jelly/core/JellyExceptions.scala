package eu.ostrzyciel.jelly.core

private trait JellyExceptions:
  sealed class RdfProtoDeserializationError(msg: String) extends Error(msg)

  final class MissingPrefixEntryError(val prefixId: Int) extends RdfProtoDeserializationError(
    s"Missing entry in prefix table at ID: $prefixId"
  )

  final class MissingNameEntryError(val nameId: Int) extends RdfProtoDeserializationError(
    s"Missing entry in name table at ID: $nameId"
  )

  final class RdfProtoSerializationError(msg: String) extends Error(msg)
  
private object JellyExceptions extends JellyExceptions

export JellyExceptions.*
