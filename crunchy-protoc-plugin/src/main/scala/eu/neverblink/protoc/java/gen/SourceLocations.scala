package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos.*

import java.util

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
 * The field and type comments are hidden inside the source info,
 * but the paths are somewhat difficult to parse. This class
 * contains utilities to match elements to their source location.
 *
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object SourceLocations:
  def createElementMap(descriptor: FileDescriptorProto): util.Map[String, SourceCodeInfo.Location] =
    val map = new util.TreeMap[String, SourceCodeInfo.Location]
    val builder = new StringBuilder(50)
    for (i <- 0 until descriptor.getSourceCodeInfo.getLocationCount) {
      val location = descriptor.getSourceCodeInfo.getLocation(i)
      map.put(getLocationIdentifier(location, descriptor, builder).toString, location)
    }
    map

  private def getLocationIdentifier(
    location: SourceCodeInfo.Location,
    desc: FileDescriptorProto,
    builder: StringBuilder
  ): StringBuilder =
    builder.setLength(0)
    val pkg = NamingUtil.getProtoPackage(desc)
    if (pkg.nonEmpty) builder.append(".").append(pkg)
    if (location.getPathCount > 0) location.getPath(0) match
      case FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER => // 4
        return appendPathId(location, desc.getMessageType(location.getPath(1)), 2, builder)
      case FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER => // 5
        return appendPathId(location, desc.getEnumType(location.getPath(1)), 2, builder)
      case FileDescriptorProto.NAME_FIELD_NUMBER => // 1
      case FileDescriptorProto.PACKAGE_FIELD_NUMBER => // 2
      case FileDescriptorProto.DEPENDENCY_FIELD_NUMBER => // 3
      case FileDescriptorProto.SERVICE_FIELD_NUMBER => // 6
      case FileDescriptorProto.EXTENSION_FIELD_NUMBER => // 7
      case FileDescriptorProto.OPTIONS_FIELD_NUMBER => // 8
      case FileDescriptorProto.SOURCE_CODE_INFO_FIELD_NUMBER => // 9
      case FileDescriptorProto.PUBLIC_DEPENDENCY_FIELD_NUMBER => // 10
      case FileDescriptorProto.WEAK_DEPENDENCY_FIELD_NUMBER => // 11
      case FileDescriptorProto.SYNTAX_FIELD_NUMBER => // 12
      case _ =>
        return addUnsupported(location, 0, builder)
    builder

  private def appendPathId(
    location: SourceCodeInfo.Location,
    desc: DescriptorProto,
    ix: Int,
    builder: StringBuilder
  ): StringBuilder =
    builder.append(".").append(desc.getName)
    if (location.getPathCount > ix) location.getPath(ix) match {
      case DescriptorProto.FIELD_FIELD_NUMBER => // 2
        return appendPathId(location, desc.getField(location.getPath(ix + 1)), ix + 2, builder)
      case DescriptorProto.NESTED_TYPE_FIELD_NUMBER => // 3
        return appendPathId(location, desc.getNestedType(location.getPath(ix + 1)), ix + 2, builder)
      case DescriptorProto.ENUM_TYPE_FIELD_NUMBER => // 4
        return appendPathId(location, desc.getEnumType(location.getPath(ix + 1)), ix + 2, builder)
      case DescriptorProto.NAME_FIELD_NUMBER => // 1
      case DescriptorProto.EXTENSION_RANGE_FIELD_NUMBER => // 5
      case DescriptorProto.EXTENSION_FIELD_NUMBER => // 6
      case DescriptorProto.OPTIONS_FIELD_NUMBER => // 7
      case DescriptorProto.ONEOF_DECL_FIELD_NUMBER => // 8
      case DescriptorProto.RESERVED_RANGE_FIELD_NUMBER => // 9
      case DescriptorProto.RESERVED_NAME_FIELD_NUMBER => // 10
      case _ =>
        return addUnsupported(location, ix, builder)
    }
    builder

  private def appendPathId(
    location: SourceCodeInfo.Location, 
    desc: FieldDescriptorProto, 
    ix: Int, 
    builder: StringBuilder
  ): StringBuilder =
    builder.append(".").append(desc.getName)
    if (location.getPathCount > ix) location.getPath(ix) match
      case FieldDescriptorProto.NAME_FIELD_NUMBER => // 1
      case FieldDescriptorProto.EXTENDEE_FIELD_NUMBER => // 2
      case FieldDescriptorProto.NUMBER_FIELD_NUMBER => // 3
      case FieldDescriptorProto.LABEL_FIELD_NUMBER => // 4
      case FieldDescriptorProto.TYPE_FIELD_NUMBER => // 5
      case FieldDescriptorProto.TYPE_NAME_FIELD_NUMBER => // 6
      case FieldDescriptorProto.DEFAULT_VALUE_FIELD_NUMBER => // 7
      case FieldDescriptorProto.OPTIONS_FIELD_NUMBER => // 8
      case FieldDescriptorProto.ONEOF_INDEX_FIELD_NUMBER => // 9
      case FieldDescriptorProto.JSON_NAME_FIELD_NUMBER => // 10
      case _ =>
        return addUnsupported(location, ix, builder)
    builder

  private def appendPathId(
    location: SourceCodeInfo.Location, 
    desc: EnumDescriptorProto, 
    ix: Int, 
    builder: StringBuilder
  ): StringBuilder =
    builder.append(".").append(desc.getName)
    if (location.getPathCount > ix) location.getPath(ix) match
      case EnumDescriptorProto.VALUE_FIELD_NUMBER => // 2
        return appendPathId(location, desc.getValue(location.getPath(ix + 1)), ix + 2, builder)
      case EnumDescriptorProto.NAME_FIELD_NUMBER => // 1
      case EnumDescriptorProto.OPTIONS_FIELD_NUMBER => // 3
      case EnumDescriptorProto.RESERVED_RANGE_FIELD_NUMBER => // 4
      case EnumDescriptorProto.RESERVED_NAME_FIELD_NUMBER => // 5
      case _ =>
        return addUnsupported(location, ix, builder)
    builder

  private def appendPathId(
    location: SourceCodeInfo.Location,
    desc: EnumValueDescriptorProto, 
    ix: Int, 
    builder: StringBuilder
  ): StringBuilder =
    builder.append(".").append(desc.getName)
    if (location.getPathCount > ix) location.getPath(ix) match
      case EnumValueDescriptorProto.NAME_FIELD_NUMBER => // 1
      case EnumValueDescriptorProto.NUMBER_FIELD_NUMBER => // 2
      case EnumValueDescriptorProto.OPTIONS_FIELD_NUMBER => // 3
      case _ =>
        return addUnsupported(location, ix, builder)
    builder

  private def addUnsupported(
    location: SourceCodeInfo.Location, 
    ix: Int, 
    builder: StringBuilder
  ) =
    builder.append(".{")
    for (i <- ix until location.getPathCount) {
      builder.append(location.getPath(i)).append(',')
    }
    if (builder.charAt(builder.length - 1) == ',') builder.setLength(builder.length - 1)
    builder.append("}")
    builder
