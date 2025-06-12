package eu.neverblink.jelly.grpc.utils

import eu.neverblink.protoc.java.runtime.ProtoMessage
import io.grpc.KnownLength
import org.apache.pekko.annotation.InternalStableApi
import org.apache.pekko.grpc.ProtobufSerializer
import org.apache.pekko.grpc.internal.BaseMarshaller

import java.io.{ByteArrayInputStream, InputStream}

@InternalStableApi
class CrunchyMarshaller[T <: ProtoMessage[T]](protobufSerializer: ProtobufSerializer[T])
  extends BaseMarshaller[T](protobufSerializer) {
  override def parse(stream: InputStream): T = super.parse(stream)
  override def stream(value: T): InputStream =
    new ByteArrayInputStream(value.toByteArray) with KnownLength
}
