package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.*
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import com.palantir.javapoet.*
import eu.neverblink.protoc.java.gen.Preconditions.*

import java.util
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import scala.collection.mutable
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

class RequestInfo(val descriptor: CodeGeneratorRequest):
  final private val typeRegistry = TypeRegistry.empty

  val pluginOptions = new PluginOptions(descriptor)
  val files: util.List[RequestInfo.FileInfo] = descriptor.getProtoFileList.stream
    .map((desc: DescriptorProtos.FileDescriptorProto) => new RequestInfo.FileInfo(this, desc))
    .collect(Collectors.toList)

  def getInfoForFile(fileName: String): RequestInfo.FileInfo =
    files.asScala
      .find(_.fileName == fileName)
      .getOrElse(throw new IllegalArgumentException("File was not found in this request: " + fileName))

  def shouldEnumUseArrayLookup(lowestNumber: Int, highestNumber: Int): Boolean =
    lowestNumber >= 0 && highestNumber < 50 // parameter?


/**
 * Meta info that wraps the information in descriptors in a format that is easier to work with
 *
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object RequestInfo:
  def withoutTypeMap(request: CodeGeneratorRequest) = new RequestInfo(request)

  def withTypeRegistry(request: CodeGeneratorRequest): RequestInfo =
    val info = new RequestInfo(request)
    info.typeRegistry.registerContainedTypes(info)
    info

  class FileInfo(
    val parentRequest: RequestInfo, 
    val descriptor: DescriptorProtos.FileDescriptorProto
  ) {
    private val sourceMap: util.Map[String, SourceCodeInfo.Location] = SourceLocations.createElementMap(descriptor)
    val fileName: String = descriptor.getName
    private val protoPackage: String = NamingUtil.getProtoPackage(descriptor)
    val javaPackage: String = parentRequest.pluginOptions.replacePackageFunction.apply(NamingUtil.getJavaPackage(descriptor))
    val outerClassName: ClassName = ClassName.get(javaPackage, NamingUtil.getJavaOuterClassname(descriptor))
    val outputDirectory: String = if (javaPackage.isEmpty) "" else javaPackage.replaceAll("\\.", "/") + "/"
    val options: DescriptorProtos.FileOptions = descriptor.getOptions
    val generateMultipleFiles: Boolean = !options.hasJavaMultipleFiles || options.getJavaMultipleFiles
    val generateDescriptors: Boolean = parentRequest.pluginOptions.generateDescriptors
    val deprecated: Boolean = options.hasDeprecated && options.getDeprecated
    // The first message appends a '.', so we omit it to not have two '.' in the default package
    private val baseTypeId: String = if (protoPackage.isEmpty) "" else "." + protoPackage
    val messageTypes: util.List[RequestInfo.MessageInfo] = descriptor.getMessageTypeList.stream.map(
      (desc: DescriptorProtos.DescriptorProto) =>
        new RequestInfo.MessageInfo(this, baseTypeId, outerClassName, !generateMultipleFiles, desc)
    ).collect(Collectors.toList)
    val enumTypes: util.List[RequestInfo.EnumInfo] = descriptor.getEnumTypeList.stream.map(
      (desc: DescriptorProtos.EnumDescriptorProto) =>
        new RequestInfo.EnumInfo(this, baseTypeId, outerClassName, !generateMultipleFiles, desc)
    ).collect(Collectors.toList)

    def getSourceLocation(identifier: String): SourceCodeInfo.Location = sourceMap.getOrDefault(identifier, SourceCodeInfo.Location.getDefaultInstance)
  }

  abstract class TypeInfo(
    val parentFile: RequestInfo.FileInfo,
    parentTypeId: String,
    parentType: ClassName,
    val isNested: Boolean,
    name: String
  ) {
    val typeName: ClassName = if (isNested) parentType.nestedClass(name) else parentType.peerClass(name)
    val fieldNamesClass: ClassName = this.typeName.nestedClass("FieldNames")
    val typeId: String = parentTypeId + "." + name
    val fullName: String = if (typeId.startsWith(".")) typeId.substring(1) else typeId
    val sourceLocation: SourceCodeInfo.Location = parentFile.getSourceLocation(typeId)
  }

  class MessageInfo(
    parentFile: RequestInfo.FileInfo,
    parentTypeId: String,
    parentType: ClassName,
    isNested: Boolean,
    val descriptor: DescriptorProtos.DescriptorProto
  ) extends RequestInfo.TypeInfo(
    parentFile, parentTypeId, parentType, isNested, descriptor.getName
  ) {
    val mutableTypeName: ClassName = typeName.nestedClass("Mutable")
    val fieldCount: Int = descriptor.getFieldCount
    val options: PluginOptions = parentFile.parentRequest.pluginOptions
    // Extensions in embedded mode: treat extension fields the same as normal
    // fields and embed them directly into the message.
    private var fieldList: java.util.List[DescriptorProtos.FieldDescriptorProto] = descriptor.getFieldList
    private val nameCollisions = new java.util.HashSet[String]
    val nameCollisionCheck: Any => Boolean = nameCollisions.contains
    val request: RequestInfo = parentFile.parentRequest
    // Sort fields by serialization order such that they are accessed in a
    // sequential access pattern.
    val sortedFields: java.util.List[DescriptorProtos.FieldDescriptorProto] = 
      fieldList.stream
        .sorted(FieldUtil.MemoryLayoutSorter)
        .collect(Collectors.toList)
    // Build bitfield index map. In the case of OneOf fields we want them grouped
    // together so that we can check all has states in as few bitfield comparisons
    // as possible. If there are no OneOf fields, the order will match the field
    // order.
    var bitIndex = 0
    private val bitIndices = new java.util.HashMap[DescriptorProtos.FieldDescriptorProto, Integer]

    for (desc <- sortedFields.stream
      .sorted(FieldUtil.GroupOneOfAndRequiredBits)
      .collect(Collectors.toList).asScala
    ) {
      bitIndices.put(desc, {
        bitIndex += 1; bitIndex - 1
      })
    }

    val implements: Seq[String] = parentFile.parentRequest.pluginOptions.implements
      .getOrElse(typeName.simpleName(), Seq())
    val implementsMutable: Seq[String] = parentFile.parentRequest.pluginOptions.implements
      .getOrElse(typeName.simpleName() + ".Mutable", Seq())
    // Build map
    val fields: mutable.Buffer[FieldInfo] = for desc <- sortedFields.asScala yield
      new RequestInfo.FieldInfo(parentFile, this, typeName, desc, bitIndices.get(desc))
    val nestedTypes: util.List[RequestInfo.MessageInfo] = descriptor.getNestedTypeList.stream.map(
      (desc: DescriptorProtos.DescriptorProto) => new RequestInfo.MessageInfo(parentFile, typeId, typeName, true, desc)
    ).collect(Collectors.toList)
    val nestedEnums: util.List[RequestInfo.EnumInfo] = descriptor.getEnumTypeList.stream.map(
      (desc: DescriptorProtos.EnumDescriptorProto) => new RequestInfo.EnumInfo(parentFile, typeId, typeName, true, desc)
    ).collect(Collectors.toList)

    private val oneOfCount: Int = descriptor.getOneofDeclCount
    val oneOfs: IndexedSeq[OneOfInfo] = for i <- 0 until oneOfCount yield
      new RequestInfo.OneOfInfo(
        parentFile, this, typeName, descriptor.getOneofDecl(i), i
      )

    val isEmptyMessage: Boolean = fieldCount == 0 && oneOfCount == 0
  }

  class FieldInfo(
    val parentFile: RequestInfo.FileInfo,
    val parentTypeInfo: RequestInfo.MessageInfo,
    val parentType: ClassName,
    val descriptor: DescriptorProtos.FieldDescriptorProto,
    val bitIndex: Int
  ) {
    val fieldId: String = parentTypeInfo.typeId + "." + descriptor.getName
    val sourceLocation: SourceCodeInfo.Location = parentFile.getSourceLocation(fieldId)
    private var upperCaseName: String = null
    if (isGroup) {
      // name is all lowercase, so convert the type name instead (e.g. ".package.OptionalGroup")
      val name = descriptor.getTypeName
      val packageEndIndex = name.lastIndexOf('.')
      upperCaseName = if (packageEndIndex > 0) name.substring(packageEndIndex + 1)
      else name
    }
    else upperCaseName = NamingUtil.toUpperCamel(descriptor.getName)
    if (
      NamingUtil.isCollidingFieldName(descriptor.getName) ||
        (descriptor.hasExtendee && parentTypeInfo.nameCollisionCheck.apply(descriptor.getName))
    ) upperCaseName += descriptor.getNumber
    val upperName: String = upperCaseName
    private val lowerName: String = Character.toLowerCase(upperName.charAt(0)).toString + upperName.substring(1)
    val setterName: String = "set" + upperName
    val getterName: String = "get" + upperName
    val hazzerName: String = "has" + upperName
    val tryGetName: String = "tryGet" + upperName
    val adderName: String = "add" + upperName
    val lazyInitName: String = "init" + upperName
    val isPrimitive: Boolean = FieldUtil.isPrimitive(descriptor.getType)
    val tag: Int = FieldUtil.makeTag(descriptor)
    val bytesPerTag: Int = FieldUtil.computeRawVarint32Size(tag) + (if (!isGroup) 0 else FieldUtil.computeRawVarint32Size(getEndGroupTag))
    val packedTag: Int = FieldUtil.makePackedTag(descriptor)
    val number: Int = descriptor.getNumber
    val fieldName: String = NamingUtil.filterKeyword(lowerName)
    private val defValue: String = FieldUtil.getEmptyDefaultValue(descriptor.getType)
    val defaultValue: String = if (isEnum) NamingUtil.filterKeyword(defValue) else defValue
    private val repeatedStoreType: ClassName = RuntimeClasses.getRepeatedStoreType(descriptor.getType)
    val methodAnnotations: util.List[AnnotationSpec] = if (isDeprecated)
      Collections.singletonList(AnnotationSpec.builder(classOf[Deprecated]).build)
    else Collections.emptyList

    // Original field name (under_score).
    val protoFieldName: String = descriptor.getName

    private def getRepeatedStoreType: TypeName =
      if (isGroup || isMessage) ParameterizedTypeName.get(repeatedStoreType, getTypeName)
      else if (isEnum) ParameterizedTypeName.get(repeatedStoreType, getTypeName)
      else repeatedStoreType

    def isFixedWidth: Boolean = FieldUtil.isFixedWidth(descriptor.getType)

    def getFixedWidth: Int =
      checkState(isFixedWidth, "not a fixed width type")
      FieldUtil.getFixedWidth(descriptor.getType)

    def isMessageOrGroup: Boolean = isMessage || isGroup

    def getDefaultFieldName: String = "_default" + upperName

    def getInputParameterType: TypeName = descriptor.getType match
      case FieldDescriptorProto.Type.TYPE_STRING =>
        TypeName.get(classOf[CharSequence])
      case FieldDescriptorProto.Type.TYPE_BYTES =>
        if (isRepeated) ArrayTypeName.of(TypeName.BYTE) else TypeName.BYTE
      case _ => getTypeName

    def isPresenceEnabled: Boolean =
      // Checks whether field presence is enabled for this field. See
      // https://github.com/protocolbuffers/protobuf/blob/main/docs/implementing_proto3_presence.md
      //
      // Disabling field presence should:
      // * not generate a has method
      // * not modify bit fields
      // * serialize and compute size when the field value is not zero
      //
      // We only support proto3.
      isMessageOrGroup || isRepeated

    def pluginOptions: PluginOptions = parentFile.parentRequest.pluginOptions

    def getEndGroupTag: Int = FieldUtil.makeGroupEndTag(tag)

    def isGroup: Boolean = descriptor.getType eq FieldDescriptorProto.Type.TYPE_GROUP

    def isMessage: Boolean = descriptor.getType eq FieldDescriptorProto.Type.TYPE_MESSAGE

    def isString: Boolean = descriptor.getType eq FieldDescriptorProto.Type.TYPE_STRING

    def isBytes: Boolean = descriptor.getType eq FieldDescriptorProto.Type.TYPE_BYTES

    def isEnum: Boolean = descriptor.getType eq FieldDescriptorProto.Type.TYPE_ENUM

    def isRequired: Boolean = descriptor.getLabel eq FieldDescriptorProto.Label.LABEL_REQUIRED

    def isOptional: Boolean = descriptor.getLabel eq FieldDescriptorProto.Label.LABEL_OPTIONAL

    def isRepeated: Boolean = descriptor.getLabel eq FieldDescriptorProto.Label.LABEL_REPEATED

    private def isSingular: Boolean = !isRepeated

    def isPacked: Boolean = isPackable && descriptor.getOptions.hasPacked && descriptor.getOptions.getPacked

    def isSingularPrimitiveOrEnum: Boolean = isSingular && (isPrimitive || isEnum)

    def isPackable: Boolean =
      if (!isRepeated) return false
      descriptor.getType match
        case FieldDescriptorProto.Type.TYPE_STRING => false
        case FieldDescriptorProto.Type.TYPE_GROUP => false
        case FieldDescriptorProto.Type.TYPE_MESSAGE => false
        case FieldDescriptorProto.Type.TYPE_BYTES => false
        case _ => true

    private def isDeprecated: Boolean = descriptor.getOptions.hasDeprecated && descriptor.getOptions.getDeprecated

    def getTypeName: TypeName =
      // Lazy because type registry is not constructed at creation time
      parentFile.parentRequest.typeRegistry.resolveJavaTypeFromProto(descriptor)

    def isMessageOrGroupWithRequiredFieldsInHierarchy: Boolean =
      // Lazy because type registry is not constructed at creation time
      isMessageOrGroup && parentFile.parentRequest.typeRegistry.hasRequiredFieldsInHierarchy(getTypeName)

    def getStoreType: TypeName =
      if (isRepeated) return getRepeatedStoreType
      if (isString) return RuntimeClasses.StringType
      if (isEnum) return TypeName.INT
      getTypeName

    // Used for the return type in the method, e.g., Optional<String>
    private def getOptionalReturnType: TypeName =
      if (isRepeated) return ParameterizedTypeName.get(ClassName.get(classOf[Optional[_]]), getRepeatedStoreType)
      val typeName = getTypeName
      if (!isPrimitive || (typeName eq TypeName.BOOLEAN)) 
        return ParameterizedTypeName.get(ClassName.get(classOf[Optional[_]]), typeName.box)
      if (typeName eq TypeName.INT) return TypeName.get(classOf[OptionalInt])
      if (typeName eq TypeName.LONG) return TypeName.get(classOf[OptionalLong])
      if (typeName eq TypeName.FLOAT) return TypeName.get(classOf[OptionalDouble])
      if (typeName eq TypeName.DOUBLE) return TypeName.get(classOf[OptionalDouble])
      throw new IllegalArgumentException("Unhandled type: " + typeName)

    // Used for creating the optional, e.g., Optional.of(string)
    def getOptionalClass: TypeName =
      val t = getOptionalReturnType
      t match
        case name: ParameterizedTypeName => name.rawType
        case _ => t

    def isEmptyMessage: Boolean =
      if isMessageOrGroup then
        parentFile.parentRequest.typeRegistry
          .resolveMessageInfoFromProto(descriptor).isEmptyMessage
      else false
  }

  class EnumInfo(
    parentFile: RequestInfo.FileInfo, 
    parentTypeId: String, 
    parentType: ClassName,
    isNested: Boolean, 
    val descriptor: DescriptorProtos.EnumDescriptorProto
  ) extends RequestInfo.TypeInfo(
    parentFile, parentTypeId, parentType, isNested, descriptor.getName
  ) {
    private var low = 0
    private var high = 0
    val usedFields = new java.util.HashSet[Integer]

    val values = new java.util.ArrayList[RequestInfo.EnumValueInfo]
    val aliases = new java.util.ArrayList[RequestInfo.EnumValueInfo]

    val nameInSnakeCase: String = descriptor.getName
      .split("(?=\\p{Upper})")
      .map(_.toUpperCase)
      .mkString("_")

    for (value <- descriptor.getValueList.asScala) {
      if (usedFields.add(value.getNumber)) {
        values.add(new RequestInfo.EnumValueInfo(this, value))
        low = Math.min(low, value.getNumber)
        high = Math.max(high, value.getNumber)
      }
      else aliases.add(new RequestInfo.EnumValueInfo(this, value))
    }
    private val lowestNumber: Int = low
    val highestNumber: Int = high
    val usingArrayLookup: Boolean = parentFile.parentRequest.shouldEnumUseArrayLookup(lowestNumber, highestNumber)

    def findAliasedValue(alias: RequestInfo.EnumValueInfo): RequestInfo.EnumValueInfo =
      values.asScala
        .find(_.getNumber == alias.getNumber)
        .getOrElse(throw new IllegalArgumentException("Enum value does not have an alias"))
  }

  class EnumValueInfo(
    var parentType: RequestInfo.EnumInfo, 
    var descriptor: DescriptorProtos.EnumValueDescriptorProto,
  ) {
    private val valueId: String = parentType.typeId + "." + descriptor.getName
    val sourceLocation: SourceCodeInfo.Location = parentType.parentFile.getSourceLocation(valueId)

    // Simplify names like in scalapb
    def getName: String = descriptor.getName.replace(parentType.nameInSnakeCase + "_", "")

    def getNumber: Int = descriptor.getNumber
  }

  class OneOfInfo(
    val parentFile: RequestInfo.FileInfo, 
    val parentTypeInfo: RequestInfo.MessageInfo, 
    val parentType: ClassName,
    val descriptor: DescriptorProtos.OneofDescriptorProto,
    val oneOfIndex: Int
  ) {

    val upperName: String = NamingUtil.toUpperCamel(descriptor.getName)
    val fieldName: String = {
      val lowerName = Character.toLowerCase(upperName.charAt(0)).toString + upperName.substring(1)
      NamingUtil.filterKeyword(lowerName)
    }
    val numberFieldName: String = fieldName + "Number"
    val getterName: String = "get" + upperName
    val getNumberName: String = "get" + upperName + "FieldNumber"
    val setterName: String = "set" + upperName
    val hazzerName: String = "has" + upperName
    val clearName: String = "clear" + upperName

    def getFields: Seq[RequestInfo.FieldInfo] = parentTypeInfo.fields
      .filter(field => field.descriptor.hasOneofIndex)
      .filter(field => field.descriptor.getOneofIndex eq oneOfIndex)
      .toSeq
  }

