package pl.ostrzyciel.jelly

package object core:
  final class RDFProtobufDeserializationError(msg: String) extends Error(msg)

  final class RDFProtobufSerializationError(msg: String) extends Error(msg)
