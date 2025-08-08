package eu.neverblink.jelly.pekko.stream

import com.google.protobuf.CodedInputStream
import eu.neverblink.protoc.java.runtime.{MessageFactory, ProtoMessage}
import org.apache.pekko.util.ByteString

import scala.jdk.CollectionConverters.*

/** Utilities for using Jelly-JVM together with Apache Pekko.
  */
object PekkoUtil:
  /** Parses a Protobuf message from a Pekko ByteString.
    *
    * The input ByteString must contain a single Protobuf message, with no additional framing or
    * length prefix.
    *
    * This method should be preferred over constructing an InputStream from the ByteString, as it
    * gives the parser more direct access to the underlying data structure.
    *
    * @param input
    *   The ByteString containing the Protobuf message data.
    * @param messageFactory
    *   The factory to create the message instance.
    * @tparam T
    *   The type of the message to parse
    * @return
    *   parsed message
    */
  def parseFromByteString[T <: ProtoMessage[T]](
      input: ByteString,
      messageFactory: MessageFactory[T],
  ): T =
    val message = messageFactory.create()
    val byteBuffers = input.asByteBuffers
    // If the ByteString contains only one ByteBuffer, we pass it directly to CodedInputStream.
    // CodedInputStream will then apply extra optimizations for faster parsing.
    val codedInputStream =
      if byteBuffers.size == 1 then CodedInputStream.newInstance(byteBuffers.head)
      else CodedInputStream.newInstance(byteBuffers.asJava)
    ProtoMessage.mergeFrom(message, codedInputStream, ProtoMessage.DEFAULT_MAX_RECURSION_DEPTH)
