package eu.ostrzyciel.jelly.core

private trait JellyExceptions:
  sealed abstract class RdfProtoError(msg: String, cause: Option[Throwable]) extends Error(msg):
    if cause.isDefined then initCause(cause.get)
  
  final class RdfProtoDeserializationError(msg: String, cause: Option[Throwable] = None) 
    extends RdfProtoError(msg, cause)

  final class RdfProtoSerializationError(msg: String, cause: Option[Throwable] = None) 
    extends RdfProtoError(msg, cause)

  final class RdfProtoTranscodingError(msg: String, cause: Option[Throwable] = None) 
    extends RdfProtoError(msg, cause)
  
private object JellyExceptions extends JellyExceptions:
  /**
   * Helper method to allow Java code to throw a [[RdfProtoSerializationError]].
   * @param msg error message
   * @return an instance of [[RdfProtoSerializationError]]
   */
  private[core] def rdfProtoDeserializationError(msg: String): RdfProtoDeserializationError =
    new RdfProtoDeserializationError(msg)

export JellyExceptions.*
