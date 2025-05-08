package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.*
import com.palantir.javapoet.{ClassName, TypeName}
import eu.neverblink.protoc.java.gen.Preconditions.*
import eu.neverblink.protoc.java.gen.RequestInfo.{MessageInfo, TypeInfo}
import eu.neverblink.protoc.java.gen.TypeRegistry.RequiredType

import scala.jdk.CollectionConverters.*

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

object TypeRegistry:
  def empty = new TypeRegistry

  enum RequiredType:
    case Required, Optional, Processing


class TypeRegistry:
  private final val typeMap = new java.util.HashMap[String, ClassName]
  private final val messageMap = new java.util.HashMap[TypeName, MessageInfo]
  private final val hasRequiredMap = new java.util.HashMap[TypeName, RequiredType]
  
  def resolveMessageInfoFromProto(descriptor: FieldDescriptorProto): MessageInfo =
    val typeId = descriptor.getTypeName
    val typeName = checkNotNull(typeMap.get(typeId), "Unable to resolve type id: " + typeId)
    val messageInfo = messageMap.get(typeName)
    checkNotNull(messageInfo, "Unable to resolve message info for: " + typeName)
    messageInfo
  
  def resolveJavaTypeFromProto(descriptor: FieldDescriptorProto): TypeName =
    descriptor.getType match
      case TYPE_DOUBLE => TypeName.DOUBLE
      case TYPE_FLOAT => TypeName.FLOAT
      case TYPE_SFIXED64 => TypeName.LONG
      case TYPE_FIXED64 => TypeName.LONG
      case TYPE_SINT64 => TypeName.LONG
      case TYPE_INT64 => TypeName.LONG
      case TYPE_UINT64 => TypeName.LONG
      case TYPE_SFIXED32 => TypeName.INT
      case TYPE_FIXED32 => TypeName.INT
      case TYPE_SINT32 => TypeName.INT
      case TYPE_INT32 => TypeName.INT
      case TYPE_UINT32 => TypeName.INT
      case TYPE_BOOL => TypeName.BOOLEAN
      case TYPE_STRING => TypeName.get(classOf[String])
      case TYPE_ENUM => resolveMessageType(descriptor.getTypeName)
      case TYPE_GROUP => resolveMessageType(descriptor.getTypeName)
      case TYPE_MESSAGE => resolveMessageType(descriptor.getTypeName)
      case TYPE_BYTES => RuntimeClasses.BytesType

  private def resolveMessageType(typeId: String): ClassName =
    checkNotNull(typeMap.get(typeId), "Unable to resolve type id: " + typeId)

  def registerContainedTypes(info: RequestInfo): Unit =
    typeMap.clear()
    for (file <- info.files.asScala) {
      file.messageTypes.forEach(registerType)
      file.enumTypes.forEach(registerType)
    }

  private def registerType(typeInfo: TypeInfo): Unit =
    if (typeMap.containsValue(typeInfo.typeName)) 
      throw new Exception("Duplicate class name: " + typeInfo.typeName)
    if (typeMap.put(typeInfo.typeId, typeInfo.typeName) != null)
      throw new Exception("Duplicate type id: " + typeInfo.typeId)
    typeInfo match
      case msgInfo: MessageInfo =>
        msgInfo.nestedTypes.forEach(registerType)
        msgInfo.nestedEnums.forEach(registerType)
        messageMap.put(typeInfo.typeName, msgInfo)
      case _ =>

  /**
   * Checks message types for any required fields in their hierarchy. Many
   * cases don't have any or very few required fields, so we don't need to
   * check the messages that will always return true anyways.
   */
  def hasRequiredFieldsInHierarchy(t: TypeName): Boolean =
    if (!messageMap.containsKey(t)) throw new IllegalStateException("Not a message or group type: " + t)
    // Lazily compute for each message
    if (!hasRequiredMap.containsKey(t)) {
      hasRequiredMap.put(t, TypeRegistry.RequiredType.Processing)
      var hasRequired = false
      val info = messageMap.get(t)
      for (field <- info.fields) {
        if (isRequiredFieldOrNeedsToBeChecked(t, field)) hasRequired = true
      }
      hasRequiredMap.put(t, if (hasRequired) TypeRegistry.RequiredType.Required
      else TypeRegistry.RequiredType.Optional)
      return hasRequired
    }
    // Return cached result
    val result = hasRequiredMap.get(t)
    checkState(
      result ne TypeRegistry.RequiredType.Processing, 
      "Processing required fields did not finish"
    )
    result eq TypeRegistry.RequiredType.Required

  private def isRequiredFieldOrNeedsToBeChecked(t: TypeName, field: RequestInfo.FieldInfo): Boolean =
    // Always check message types for recursion to avoid surprises at runtime
    if (field.isMessageOrGroup) {
      val result = hasRequiredMap.get(field.getTypeName)
      if (result eq TypeRegistry.RequiredType.Processing) {
        ()
      }
      else if ((result eq TypeRegistry.RequiredType.Required) || field.isRequired) return true
      else if (result == null) return hasRequiredFieldsInHierarchy(field.getTypeName)
    }
    field.isRequired
