package eu.ostrzyciel.jelly.core

private trait JellyExceptions:
  sealed class RdfProtoDeserializationError(msg: String) extends Error(msg)

  final class RdfProtoSerializationError(msg: String) extends Error(msg)
  
private object JellyExceptions extends JellyExceptions

export JellyExceptions.*
