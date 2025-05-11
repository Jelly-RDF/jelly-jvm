package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.*
import com.palantir.javapoet.ClassName

/*-
 * #%L
 * quickbuf-generator / CrunchyProtocPlugin
 * %%
 * Copyright (C) 2019 HEBI Robotics
 * %%
 * Copyright (C) 2025 NeverBlink
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * TypeNames of all API classes that can be referenced from generated code
 *
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object RuntimeClasses:
  private val API_PACKAGE = "eu.neverblink.protoc.java.runtime"
  private val JAVA_UTIL_PACKAGE = "java.util"
  private val GOOGLE_PACKAGE = "com.google.protobuf"

  val CodedInputStream: ClassName = ClassName.get(GOOGLE_PACKAGE, "CodedInputStream")
  val LimitedCodedInputStream: ClassName = ClassName.get(API_PACKAGE, "LimitedCodedInputStream")
  val CodedOutputStream: ClassName = ClassName.get(GOOGLE_PACKAGE, "CodedOutputStream")
  val ProtoUtil: ClassName = ClassName.get(API_PACKAGE, "ProtoUtil")
  val AbstractMessage: ClassName = ClassName.get(API_PACKAGE, "ProtoMessage")
  val MessageFactory: ClassName = ClassName.get(API_PACKAGE, "MessageFactory")
  val ObjectType: ClassName = ClassName.get(classOf[Object])
  val StringType: ClassName = ClassName.get(classOf[String])
  val BytesType: ClassName = ClassName.get(GOOGLE_PACKAGE, "ByteString")
  val Exception: ClassName = ClassName.get(classOf[Exception])
  val RuntimeException: ClassName = ClassName.get(classOf[RuntimeException])
  val InvalidProtocolBufferException: ClassName = ClassName.get(GOOGLE_PACKAGE, "InvalidProtocolBufferException")
  val UninitializedMessageException: ClassName = ClassName.get(GOOGLE_PACKAGE, "UninitializedMessageException")
  val ProtoEnum: ClassName = ClassName.get(API_PACKAGE, "ProtoEnum")
  val EnumConverter: ClassName = ProtoEnum.nestedClass("EnumConverter")
  val FileDescriptor: ClassName = ClassName.get(GOOGLE_PACKAGE, "Descriptors").nestedClass("FileDescriptor")
  val FileDescriptorProto: ClassName = ClassName.get(GOOGLE_PACKAGE, "DescriptorProtos").nestedClass("FileDescriptorProto")
  val MessageDescriptor: ClassName = ClassName.get(GOOGLE_PACKAGE, "Descriptors").nestedClass("Descriptor")
  private val RepeatedDouble = ClassName.get(API_PACKAGE, "RepeatedDouble")
  private val RepeatedFloat = ClassName.get(API_PACKAGE, "RepeatedFloat")
  private val RepeatedLong = ClassName.get(API_PACKAGE, "RepeatedLong")
  private val RepeatedInt = ClassName.get(API_PACKAGE, "RepeatedInt")
  private val RepeatedBoolean = ClassName.get(API_PACKAGE, "RepeatedBoolean")
  private val RepeatedString = ClassName.get(API_PACKAGE, "RepeatedString")
  private val RepeatedBytes = ClassName.get(GOOGLE_PACKAGE, "ByteString")
  val Collection: ClassName = ClassName.get(JAVA_UTIL_PACKAGE, "Collection")
  val ArrayList: ClassName = ClassName.get(JAVA_UTIL_PACKAGE, "ArrayList")
  val RepeatedEnum: ClassName = ClassName.get(API_PACKAGE, "RepeatedEnum")
  val Collections: ClassName = ClassName.get(JAVA_UTIL_PACKAGE, "Collections")
  val Base64: ClassName = ClassName.get(JAVA_UTIL_PACKAGE, "Base64")

  def getRepeatedStoreType(t: FieldDescriptorProto.Type): ClassName = t match
    case TYPE_DOUBLE => RepeatedDouble
    case TYPE_FLOAT => RepeatedFloat
    case TYPE_SFIXED64 => RepeatedLong
    case TYPE_FIXED64 => RepeatedLong
    case TYPE_SINT64 => RepeatedLong
    case TYPE_INT64 => RepeatedLong
    case TYPE_UINT64 => RepeatedLong
    case TYPE_SFIXED32 => RepeatedInt
    case TYPE_FIXED32 => RepeatedInt
    case TYPE_SINT32 => RepeatedInt
    case TYPE_INT32 => RepeatedInt
    case TYPE_UINT32 => RepeatedInt
    case TYPE_BOOL => RepeatedBoolean
    case TYPE_ENUM => RepeatedEnum
    case TYPE_STRING => RepeatedString
    case TYPE_GROUP => Collection
    case TYPE_MESSAGE => Collection
    case TYPE_BYTES => RepeatedBytes
