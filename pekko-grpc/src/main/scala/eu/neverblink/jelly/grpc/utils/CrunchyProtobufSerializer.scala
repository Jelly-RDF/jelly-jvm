package eu.neverblink.jelly.grpc.utils

import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.apache.pekko.grpc.ProtobufSerializer
import org.apache.pekko.util.ByteString

import java.io.InputStream

class CrunchyProtobufSerializer[T <: ProtoMessage[T]](parser: InputStream => T) extends ProtobufSerializer[T] {

  override def serialize(t: T): ByteString =
    ByteString.fromArrayUnsafe(t.toByteArray)

  override def deserialize(bytes: ByteString): T =
    parser(bytes.asInputStream)
  override def deserialize(data: InputStream): T =
    parser(data)
}
