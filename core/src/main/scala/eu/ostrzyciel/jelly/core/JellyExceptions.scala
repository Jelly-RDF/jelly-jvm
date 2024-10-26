package eu.ostrzyciel.jelly.core

private trait JellyExceptions:
  sealed class RdfProtoDeserializationError(msg: String) extends Error(msg)

  final class RdfProtoSerializationError(msg: String) extends Error(msg)
  
private object JellyExceptions extends JellyExceptions:
  /**
   * Helper method to allow Java code to throw a [[RdfProtoSerializationError]].
   * @param msg error message
   * @return an instance of [[RdfProtoSerializationError]]
   */
  private[core] def rdfProtoDeserializationError(msg: String): RdfProtoDeserializationError =
    new RdfProtoDeserializationError(msg)

export JellyExceptions.*
