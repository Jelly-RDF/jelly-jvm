package eu.ostrzyciel.jelly.core.proto.v1.patch

import scalapb.{GeneratedMessage, GeneratedMessageCompanion}

trait CompanionHelper[T <: GeneratedMessage](name: String) extends GeneratedMessageCompanion[T]:
  override final lazy val javaDescriptor: com.google.protobuf.Descriptors.Descriptor =
    val jd: com.google.protobuf.Descriptors.FileDescriptor = PatchProto.javaDescriptor
    jd.findMessageTypeByName(name)
  override final lazy val scalaDescriptor: scalapb.descriptors.Descriptor =
    val sd: scalapb.descriptors.FileDescriptor = PatchProto.scalaDescriptor
    sd.messages.find(_.name == name).get
