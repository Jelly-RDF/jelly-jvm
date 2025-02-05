package eu.ostrzyciel.jelly.core.proto.v1

import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

trait CompanionHelper[T <: GeneratedMessage](name: String) extends GeneratedMessageCompanion[T]:
  override final lazy val javaDescriptor: com.google.protobuf.Descriptors.Descriptor =
    val jd: com.google.protobuf.Descriptors.FileDescriptor = RdfProto.javaDescriptor
    jd.findMessageTypeByName(name)
  override final lazy val scalaDescriptor: scalapb.descriptors.Descriptor =
    val sd: scalapb.descriptors.FileDescriptor = RdfProto.scalaDescriptor
    sd.messages.find(_.name == name).get
