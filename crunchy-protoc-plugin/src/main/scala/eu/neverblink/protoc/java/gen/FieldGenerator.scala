package eu.neverblink.protoc.java.gen

import com.palantir.javapoet.*
import eu.neverblink.protoc.java.gen.RequestInfo.FieldInfo

import java.util
import java.util.function.Consumer
import javax.lang.model.element.Modifier

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
 * This class generates all serialization logic and field related accessors.
 * It is a bit of a mess due to lots of switch statements, but I found that
 * splitting the types up similarly to how the protobuf-generator code is
 * organized makes it really difficult to find and manage duplicate code,
 * and to keep track of where things are being called.
 *
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object FieldGenerator:
  private def generateWriteVarint32(value: Int) =
    var value2 = value
    // Split tag into individual bytes
    val bytes = new Array[Int](5)
    var numBytes = 0
    var continue = true
    while continue do 
      if (value2 & ~0x7F) == 0 then
        bytes(numBytes) = value2
        numBytes += 1
        continue = false
      else
        bytes(numBytes) = (value2 & 0x7F) | 0x80
        numBytes += 1
        value2 >>>= 7
      
    // Write tag bytes as efficiently as possible
    var output = ""
    numBytes match {
      case _ =>
        for (i <- 0 until numBytes) {
          output += "output.writeRawByte((byte) " + bytes(i) + ");\n"
        }
    }
    output

  private val EMPTY_BLOCK = CodeBlock.builder.build

class FieldGenerator(val info: FieldInfo):
  val typeName: TypeName = info.getTypeName
  private val storeType = info.getStoreType
  
  // Common-variable map for named arguments
  final val m = new util.HashMap[String, Any]
  m.put("field", info.fieldName)
  m.put("default", info.defaultValue)
  if (info.isEnum) {
    m.put("default", "0")
    m.put("defaultEnumValue", info.getTypeName.toString + "." + info.defaultValue)
    m.put("protoEnum", info.getTypeName)
  }
  m.put("storeType", storeType)
  m.put("lazyInitMethod", info.lazyInitName)
  m.put("getMethod", info.getterName)
  m.put("setMethod", info.setterName)
  m.put("addMethod", info.adderName)
  m.put("message", info.parentType)
  m.put("type", typeName)
  m.put("number", info.number)
  m.put("tag", info.tag)
  m.put("capitalizedType", FieldUtil.getCapitalizedType(info.descriptor.getType))
  m.put("secondArgs", if (info.isGroup) ", " + info.number
  else "")
  m.put("defaultField", info.getDefaultFieldName)
  m.put("bytesPerTag", info.bytesPerTag)
  m.put("valueOrNumber", if (info.isEnum) "value.getNumber()"
  else "value")
  m.put("optional", info.getOptionalClass)
  if (info.isPackable) m.put("packedTag", info.packedTag)
  if (info.isFixedWidth) m.put("fixedWidth", info.getFixedWidth)
  if (info.isRepeated) m.put("getRepeatedIndex_i", 
    if (info.isPrimitive || info.isEnum) "array()[i]" else "get(i)"
  )
  // utility classes
  m.put("fieldNames", info.parentTypeInfo.fieldNamesClass)
  m.put("abstractMessage", RuntimeClasses.AbstractMessage)
  m.put("protoSource", RuntimeClasses.CodedInputStream)
  m.put("protoSink", RuntimeClasses.CodedOutputStream)
  m.put("protoUtil", RuntimeClasses.ProtoUtil)
  // Common configuration-dependent code blocks
  private val ensureFieldNotNull = lazyFieldInit

  def generateMemberFields(t: TypeSpec.Builder): Unit =
    val field = FieldSpec.builder(storeType, info.fieldName)
      .addJavadoc(Javadoc.forMessageField(info).build)
      .addModifiers(Modifier.PROTECTED)
    // if info.isRepeated && info.isMessage then field.addModifiers(Modifier.FINAL)
    if info.isRepeated || info.isBytes || info.isString then
      field.initializer(initializer)
    else if info.isMessageOrGroup then field.initializer("null")
    else if (info.isPrimitive || info.isEnum) { }
    else throw new IllegalStateException("unhandled field: " + info.descriptor)
    t.addField(field.build)

  private def initializer =
    val initializer = CodeBlock.builder
    if (info.isRepeated && info.isMessageOrGroup)
      initializer.add("new $T($T::newInstance)", RuntimeClasses.ListMessageCollection, info.getTypeName)
    else if (info.isRepeated && info.isEnum) initializer.add("$T.newEmptyInstance($T.converter())", RuntimeClasses.RepeatedEnum, info.getTypeName)
    else if (info.isRepeated) initializer.add("$T.newEmptyInstance()", storeType)
    else if (info.isBytes) initializer.add(named("$storeType:T.EMPTY"))
    else if (info.isMessageOrGroup) initializer.add(named("$storeType:T.newInstance()"))
    else if (info.isString) initializer.add(named("\"\""))
    else if (info.isPrimitive || info.isEnum) initializer.add(named("$default:L"))
    else throw new IllegalStateException("unhandled field: " + info.descriptor)
    initializer.build

  def generateClearCode(method: MethodSpec.Builder): Unit =
    if info.isSingularPrimitiveOrEnum then
      method.addStatement(named("$field:N = $default:L"))
    else if info.isRepeated then
      method.addStatement(named("$field:N.clear()"))
    else if info.isMessageOrGroup then
      method.addStatement(named("$field:N = null"))
    else if info.isString then
      method.addStatement(named("$field:N = \"\""))
    else if info.isBytes then
      method.addStatement(named("$field:N = ByteString.EMPTY"))
    else throw new IllegalStateException("unhandled field: " + info.descriptor)

  def generateCopyFromCode(method: MethodSpec.Builder): Unit =
    if (info.isSingularPrimitiveOrEnum || info.isString || info.isBytes)
      method.addStatement(named("$field:N = other.$field:N"))
    else if (info.isRepeated || info.isMessageOrGroup) {
      if info.isRepeated then method
        .addStatement(named("$field:N.clear()"))
        .addStatement(named("$field:N.addAll(other.$field:N)"))
      else method
        .addStatement(named("$lazyInitMethod:L()"))
        .addStatement(named("$field:N.copyFrom(other.$field:N)"))
    }
    else throw new IllegalStateException("unhandled field: " + info.descriptor)

  def generateMergeFromMessageCode(method: MethodSpec.Builder): Unit =
    if (info.isRepeated) method.addStatement(named("$getMethod:N().addAll(other.$field:N)"))
    else if (info.isMessageOrGroup) method.addStatement(named("$getMethod:N().mergeFrom(other.$field:N)"))
    else if (info.isBytes || info.isString) method.addStatement(named("$field:N = other.$field:N"))
    else if (info.isEnum) method.addStatement(named("$setMethod:NValue(other.$field:N)"))
    else if (info.isPrimitive) method.addStatement(named("$setMethod:N(other.$field:N)"))
    else throw new IllegalStateException("unhandled field: " + info.descriptor)

  def generateEqualsStatement(method: MethodSpec.Builder): Unit =
    if (info.isRepeated || info.isBytes || info.isString)
      method.addNamedCode("$field:N.equals(other.$field:N)", m)
    else if (info.isMessageOrGroup)
      method.addNamedCode(
        "($field:N == null && other.$field:N == null || $field:N != null && $field:N.equals(other.$field:N))",
        m
      )
    else if ((typeName eq TypeName.DOUBLE) || (typeName eq TypeName.FLOAT))
      method.addNamedCode("$protoUtil:T.isEqual($field:N, other.$field:N)", m)
    else if (info.isPrimitive || info.isEnum)
      method.addNamedCode("$field:N == other.$field:N", m)
    else throw new IllegalStateException("unhandled field: " + info.descriptor)

  /**
   * @return true if the tag needs to be read
   */
  def generateMergingCode(method: MethodSpec.Builder): Boolean =
    method.addCode(ensureFieldNotNull)
    if (info.isRepeated && info.isMessageOrGroup) {
      method.addStatement(
        "tag = $T.readRepeatedMessage($N, $T.getFactory(), inputLimited, tag)",
        RuntimeClasses.AbstractMessage, info.fieldName, info.getTypeName
      )
      return false // tag is already read, so don't read again
    } else if (info.isRepeated) {
      method.addNamedCode("tag = input.readRepeated$capitalizedType:L($field:N, tag);\n", m)
      return false // tag is already read, so don't read again
    } else if (info.isString)
      method.addStatement(named("$field:N = input.readStringRequireUtf8()"))
    else if (info.isMessageOrGroup)
      method.addStatement("$T.mergeDelimitedFrom($N, inputLimited)", RuntimeClasses.AbstractMessage, info.fieldName)
    else if (info.isBytes)
      method.addStatement(named("$field:N = input.readBytes()"))
    else if (info.isPrimitive)
      method.addStatement(named("$field:N = input.read$capitalizedType:L()"))
    else if (info.isEnum) {
      method.addStatement("final int value = input.readInt32()")
        .beginControlFlow("if ($T.forNumber(value) != null)", typeName)
        .addStatement(named("$field:N = value"))
      method.endControlFlow
    }
    else throw new IllegalStateException("unhandled field: " + info.descriptor)
    true

  /**
   * @return true if the tag needs to be read
   */
  def generateMergingCodeFromPacked(method: MethodSpec.Builder): Boolean =
    if (!info.isPackable) throw new IllegalStateException("not a packable type: " + info.descriptor)
    method.addCode(ensureFieldNotNull)
    if (info.isFixedWidth) method.addStatement(named("input.readPacked$capitalizedType:L($field:N)"))
    else method.addStatement(named("input.readPacked$capitalizedType:L($field:N, tag)"))
    true

  def generateHasChecker(code: CodeBlock.Builder): Unit =
    if info.isRepeated && info.isMessage then code.addNamed("!$field:N.isEmpty()", m)
    else if info.isRepeated then code.addNamed("$field:N.size() > 0", m)
    else if info.isMessage then code.addNamed("$field:N != null", m)
    else if info.isEnum then code.addNamed("$field:N != 0", m)
    else if info.isString then code.addNamed("!$field:N.isEmpty()", m)
    else if info.isBytes then code.addNamed("$field:N.size() > 0", m)
    else code.addNamed("$field:N != $default:L", m)

  def generateSerializationCode(method: MethodSpec.Builder): Unit =
    m.put("writeTagToOutput", FieldGenerator.generateWriteVarint32(info.tag))
    if (info.isPacked) m.put(
      "writePackedTagToOutput",
      FieldGenerator.generateWriteVarint32(info.packedTag)
    )
    m.put(
      "writeEndGroupTagToOutput",
      if (!info.isGroup) "" else FieldGenerator.generateWriteVarint32(info.getEndGroupTag)
    )
    if (info.isPacked) method.addNamedCode("" +
      "$writePackedTagToOutput:L" +
      "output.writePacked$capitalizedType:LNoTag($field:N);\n",
      m
    )
    else if (info.isRepeated) method.addNamedCode("" + 
      "for (final var _field : $field:N) {$>\n" +
      "$writeTagToOutput:L" +
      "output.writeUInt32NoTag(_field.getCachedSize());\n" +
      "_field.writeTo(output);\n" +
      "$writeEndGroupTagToOutput:L" + 
      "$<}\n", 
      m
    )
    else if (info.isEmptyMessage) method.addNamedCode("" +
      "$writeTagToOutput:L" +
      "// Message is always empty: write length zero\n" +
      "output.writeRawByte((byte) 0);\n",
      m
    )
    else if (info.isMessageOrGroup) method.addNamedCode("" + // non-repeated
      "$writeTagToOutput:L" +
      "output.writeUInt32NoTag($field:N.getCachedSize());\n" +
      "$field:N.writeTo(output);\n" +
      "$writeEndGroupTagToOutput:L",
      m
    )
    else {
      // unroll varint tag loop
      method.addNamedCode("" + // non-repeated
        "$writeTagToOutput:L" + 
        "output.write$capitalizedType:LNoTag($field:N);\n" + 
        "$writeEndGroupTagToOutput:L", 
        m
      )
    }

  def generateComputeSerializedSizeCode(method: MethodSpec.Builder): Unit =
    if (info.isFixedWidth && info.isPacked) method.addNamedCode("" + 
      "final int dataSize = $fixedWidth:L * $field:N.length();\n" + 
      "size += $bytesPerTag:L + $protoSink:T.computeDelimitedSize(dataSize);\n", 
      m
    )
    else if (info.isFixedWidth && info.isRepeated) { // non packed
      method.addStatement(named("size += ($bytesPerTag:L + $fixedWidth:L) * $field:N.length()"))
    }
    else if (info.isPacked) method.addNamedCode("" + 
      "final int dataSize = $abstractMessage:T.computeRepeated$capitalizedType:LSizeNoTag($field:N);\n" +
      "size += $bytesPerTag:L + $abstractMessage:T.computeDelimitedSize(dataSize);\n",
      m
    )
    else if (info.isRepeated) { // non packed
      method.addNamedCode("" + 
        "size += " +
        // if 1 byte per tag, we can skip the multiplication
        (if info.bytesPerTag > 1 then "($bytesPerTag:L * $field:N.size())" else "$field:N.size()") +
        " + $abstractMessage:T.computeRepeated$capitalizedType:LSizeNoTag($field:N);\n",
        m
      )
    }
    else if (info.isFixedWidth) 
      method.addStatement("size += $L", info.bytesPerTag + info.getFixedWidth) // non-repeated
    else if (info.isEmptyMessage)
      method.addStatement("size += $L", info.bytesPerTag + 1) // 1 byte for varint "0"
    else if (info.isMessageOrGroup) {
      method.addNamedCode(
        "final int dataSize = $field:N$secondArgs:L.getSerializedSize();\n" +
        "size += $bytesPerTag:L + $protoSink:T.computeUInt32SizeNoTag(dataSize) + dataSize;\n",
        m
      )
    }
    else method.addStatement(named(
      "size += $bytesPerTag:L + $protoSink:T.compute$capitalizedType:LSizeNoTag($field:N)"
    )) // non-repeated

  def generateMemberMethods(t: TypeSpec.Builder, tMutable: TypeSpec.Builder): Unit =
    generateInitializedMethod(tMutable)
    generateGetMethods(t)
    if (info.isEnum) generateExtraEnumAccessors(t)
    generateSetMethods(tMutable)

  private def generateInitializedMethod(t: TypeSpec.Builder): Unit =
    if !info.isRepeated && info.isMessage then
      t.addMethod(MethodSpec.methodBuilder(info.lazyInitName)
        .addModifiers(Modifier.PRIVATE)
        .addCode(CodeBlock.builder
          .beginControlFlow("if ($N == null)", info.fieldName)
          .add(named("$field:N = "))
          .addStatement(initializer)
          .endControlFlow
          .build
        ).build
      )

  private def lazyFieldInit = if !info.isRepeated && info.isMessageOrGroup then
    CodeBlock.builder.addStatement("$N()", info.lazyInitName).build
  else FieldGenerator.EMPTY_BLOCK

  private def generateSetMethods(t: TypeSpec.Builder): Unit =
    if (info.isBytes) {
      val setBytes = MethodSpec.methodBuilder("set" + info.upperName)
        .addJavadoc(Javadoc.forMessageField(info)
          .add("\n@param values the $L to set", info.fieldName)
          .add("\n@return this")
          .build
        )
        .addAnnotations(info.methodAnnotations)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(RuntimeClasses.BytesType, "values", Modifier.FINAL)
        .returns(info.parentTypeInfo.mutableTypeName)
        .addStatement(named("$field:N = values"))
        .addStatement(named("return this"))
      t.addMethod(setBytes.build)
    } else if (info.isRepeated) {
      val setter = MethodSpec.methodBuilder(info.setterName)
        .addJavadoc(Javadoc.forMessageField(info)
          .add("\n@param value the $L to set", info.fieldName)
          .add("\n@return this")
          .build
        )
        .addAnnotations(info.methodAnnotations)
        .addModifiers(Modifier.PUBLIC)
        .returns(info.parentTypeInfo.mutableTypeName)
        .addParameter(info.getStoreType, "value", Modifier.FINAL)
        .addStatement(named("$field:N = value"))
        .addStatement(named("return this"))
        .build
      t.addMethod(setter)
      val adder = MethodSpec.methodBuilder(info.adderName)
        .addJavadoc(Javadoc.forMessageField(info)
          .add("\n@param value the $L to add", info.fieldName)
          .add("\n@return this")
          .build
        )
        .addAnnotations(info.methodAnnotations)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(info.getInputParameterType, "value", Modifier.FINAL)
        .returns(info.parentTypeInfo.mutableTypeName)
        .addCode(ensureFieldNotNull)
        .addStatement(named("$field:N.add(value)"))
        .addStatement(named("return this"))
        .build
      t.addMethod(adder)
    } else if (info.isMessageOrGroup) {
      val setter = MethodSpec.methodBuilder(info.setterName)
        .addJavadoc(Javadoc.forMessageField(info)
          .add("\n@param value the $L to set", info.fieldName)
          .add("\n@return this")
          .build
        )
        .addAnnotations(info.methodAnnotations)
        .addModifiers(Modifier.PUBLIC)
        .returns(info.parentTypeInfo.mutableTypeName)
        .addParameter(info.getInputParameterType, "value", Modifier.FINAL)
        .addStatement(named("$field:N = value"))
        .addStatement(named("return this"))
        .build
      t.addMethod(setter)
    } else if (info.isString) { // setString(Utf8String)
      t.addMethod(MethodSpec.methodBuilder(info.setterName)
        .addJavadoc(Javadoc.forMessageField(info)
          .add("\n@param value the $L to set", info.fieldName)
          .add("\n@return this")
          .build
        )
        .addAnnotations(info.methodAnnotations)
        .addModifiers(Modifier.PUBLIC)
        .returns(info.parentTypeInfo.mutableTypeName)
        .addParameter(RuntimeClasses.StringType, "value", Modifier.FINAL)
        .addStatement(named("$field:N = value"))
        .addStatement(named("return this"))
        .build
      )
    }
    else if (info.isPrimitive || info.isEnum) {
      val setter = MethodSpec.methodBuilder(info.setterName)
        .addJavadoc(Javadoc.forMessageField(info)
          .add("\n@param value the $L to set", info.fieldName)
          .add("\n@return this")
          .build
        )
        .addAnnotations(info.methodAnnotations)
        .addModifiers(Modifier.PUBLIC)
        .addParameter(info.getTypeName, "value", Modifier.FINAL)
        .returns(info.parentTypeInfo.mutableTypeName)
        .addNamedCode("" + "$field:N = $valueOrNumber:L;\n" + "return this;\n", m)
        .build
      t.addMethod(setter)
    }

  /**
   * Enums are odd because they need to be converter back and forth and they
   * don't have the same type as the internal/repeated store. The normal
   * accessors provide access to the enum value, but for performance reasons
   * we also add accessors for the internal storage type that do not require
   * conversions.
   *
   * @param t
   */
  private def generateExtraEnumAccessors(t: TypeSpec.Builder): Unit = {
    if (!info.isEnum || info.isRepeated) return
    // Overload to get the internal store without conversion
    t.addMethod(MethodSpec.methodBuilder(info.getterName + "Value")
      .addAnnotations(info.methodAnnotations)
      .addJavadoc(named("" +
        "Gets the value of the internal enum store. The result is\n" +
        "equivalent to {@link $message:T#$getMethod:N()}.getNumber().\n" +
        "\n" +
        "@return numeric wire representation"
      ))
      .addModifiers(Modifier.PUBLIC)
      .returns(classOf[Int])
      .addCode(ensureFieldNotNull)
      .addStatement(named("return $field:N"))
      .build
    )
    // Overload to set the internal value without conversion
    t.addMethod(MethodSpec.methodBuilder(info.setterName + "Value")
      .addAnnotations(info.methodAnnotations)
      .addJavadoc(named("" +
        "Sets the value of the internal enum store. This does not\n" +
        "do any validity checks, so be sure to use appropriate value\n" +
        "constants from {@link $type:T}. Setting an invalid value\n" +
        "can cause {@link $message:T#$getMethod:N()} to return null\n" +
        "\n" +
        "@param value the numeric wire value to set\n" +
        "@return this"
      ))
      .addModifiers(Modifier.PUBLIC)
      .addParameter(classOf[Int], "value", Modifier.FINAL)
      .returns(info.parentType)
      .addNamedCode("" + "$field:N = value;\n" + "return this;\n", m)
      .build
    )
  }

  private def generateGetMethods(t: TypeSpec.Builder): Unit =
    val getter = MethodSpec.methodBuilder(info.getterName)
      .addAnnotations(info.methodAnnotations)
      .addModifiers(Modifier.PUBLIC)
    if (info.isRepeated)
      getter.returns(storeType).addStatement(named("return $field:N"))
    else if (info.isString)
      getter.returns(typeName).addStatement(named("return $field:N"))
    else if (info.isEnum)
      getter.returns(typeName).addStatement(named("return $type:T.forNumber($field:N)"))
    else
      getter.returns(typeName).addStatement(named("return $field:N"))
    if (info.isRepeated || info.isMessageOrGroup || info.isBytes) {
      getter.addJavadoc(Javadoc.forMessageField(info)
        .add("\n\n@return value for this field").build
      )
      t.addMethod(getter.build)
    }
    else t.addMethod(getter.addJavadoc(Javadoc.forMessageField(info)
      .add("\n@return the $L", info.fieldName)
      .build
    ).build)

  private def named(format: String, args: AnyRef*) =
    CodeBlock.builder.addNamed(format, m).build

  private def code(c: Consumer[CodeBlock.Builder]) =
    val block = CodeBlock.builder
    c.accept(block)
    block.build
