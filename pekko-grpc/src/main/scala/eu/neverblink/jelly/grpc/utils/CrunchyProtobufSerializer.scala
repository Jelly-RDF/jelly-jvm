package eu.neverblink.jelly.grpc.utils

import eu.neverblink.jelly.pekko.stream.PekkoUtil
import eu.neverblink.protoc.java.runtime.{MessageFactory, ProtoMessage}
import org.apache.pekko.grpc.ProtobufSerializer
import org.apache.pekko.util.ByteString

import java.io.InputStream

class CrunchyProtobufSerializer[T <: ProtoMessage[T]](factory: MessageFactory[T])
  extends ProtobufSerializer[T] {

  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)

  override def deserialize(bytes: ByteString): T =
    // Parse directly from ByteString, avoiding the overhead of InputStream
    PekkoUtil.parseFromByteString(bytes, factory)

  override def deserialize(data: InputStream): T =
    ProtoMessage.parseFrom(data, factory)
}
