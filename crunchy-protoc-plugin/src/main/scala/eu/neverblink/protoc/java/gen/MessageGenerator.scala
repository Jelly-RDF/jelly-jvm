package eu.neverblink.protoc.java.gen

import com.palantir.javapoet.*
import eu.neverblink.protoc.java.gen.RequestInfo.MessageInfo

import java.io.IOException
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
 * @author Florian Enner
 * @author Piotr Sowiński
 */
class MessageGenerator(val info: MessageInfo):
  private final val allFields = info.fields
    .map(new FieldGenerator(_))
    .toSeq

  // Most of the time, we are only interested in non-oneof fields
  final val fields = allFields
    .filterNot(_.info.descriptor.hasOneofIndex)

  final val m = new java.util.HashMap[String, AnyRef]
  m.put("abstractMessage", RuntimeClasses.AbstractMessage)

  private val oneOfGenerators = info.oneOfs.map(new OneOfGenerator(_))

  def generate: TypeSpec =
    val t = TypeSpec.classBuilder(info.typeName)
      .addJavadoc(Javadoc.forMessage(info))
      .superclass(ParameterizedTypeName.get(RuntimeClasses.AbstractMessage, info.typeName))
      .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
    val tMutable = TypeSpec.classBuilder(info.mutableTypeName)
      .addJavadoc(
        "Mutable subclass of the parent class.\n" +
        "You can call setters on this class to set the values.\n" +
        "When passing the constructed message to the serializer,\n" +
        "you should use the parent class (using .asImmutable()) to\n" +
        "ensure the message won't be modified by accident."
      )
      .superclass(info.typeName)
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
    info.implements
      .map(ClassName.bestGuess)
      .foreach(t.addSuperinterface)
    info.implementsMutable
      .map(ClassName.bestGuess)
      .foreach(tMutable.addSuperinterface)
    if (info.isNested) t.addModifiers(Modifier.STATIC)
    if (!info.isNested) {
      // Note: constants from enums and fields may have the same names
      // as constants in the nested classes. This causes Java warnings,
      // but is not fatal, so we suppress those warnings in the top-most
      // class declaration /javanano
      t.addAnnotation(AnnotationSpec
        .builder(classOf[SuppressWarnings])
        .addMember("value", "$S", "hiding")
        .build
      )
    }
    // Nested Enums
    info.nestedEnums.stream
      .map(new EnumGenerator(_))
      .map(_.generate)
      .forEach(t.addType)
    // Nested Types
    info.nestedTypes.stream
      .map(new MessageGenerator(_))
      .map(_.generate)
      .forEach(t.addType)
    // newInstance() method
    val newInstanceJavadoc = Javadoc.withComments(info.sourceLocation)
    if info.isEmptyMessage then
      newInstanceJavadoc.add(
        "This message is always empty, so there is no need to create a new instance.\n" +
        "Use the static field {@code $T.EMPTY} instead.\n",
        info.typeName
      )
    t.addMethod(MethodSpec.methodBuilder("newInstance")
      .addJavadoc(newInstanceJavadoc
        .add("@return a new empty instance of {@code $T}", info.mutableTypeName)
        .build
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(info.mutableTypeName)
      .addStatement("return new $T()", info.mutableTypeName)
      .build
    )
    // EMPTY field
    t.addField(FieldSpec.builder(info.typeName, "EMPTY")
      .addJavadoc("An empty instance of this message type.")
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
      .initializer("newInstance()")
      .build
    )
    // Constructors
    // Private constructor for the parent abstract class to disallow subclassing
    t.addMethod(MethodSpec.constructorBuilder.addModifiers(Modifier.PRIVATE).build)
    tMutable.addMethod(MethodSpec.constructorBuilder.addModifiers(Modifier.PRIVATE).build)
    // Member state
    fields.foreach(_.generateMemberFields(t))
    // OneOf fields and methods
    oneOfGenerators.foreach(_.generateMemberFields(t))
    oneOfGenerators.foreach(_.generateMemberMethods(t, tMutable))
    // Fields accessors
    fields.foreach(_.generateMemberMethods(t, tMutable))
    generateCopyFrom(tMutable)
    generateMergeFromMessage(tMutable)
    generateEquals(t)
    generateWriteTo(t)
    generateSerializedSize(t)
    generateMergeFrom(tMutable)
    generateClear(tMutable)
    generateClone(t)
    // Static utilities
    oneOfGenerators.foreach(_.generateConstants(t))
    generateParseFrom(t)
    generateMessageFactory(t)
    generateAsImmutable(tMutable)
    // Descriptors
    if (info.parentFile.parentRequest.pluginOptions.generateDescriptors) generateDescriptors(t)
    t.addType(tMutable.build)
    t.build

  private def generateAsImmutable(t: TypeSpec.Builder): Unit =
    t.addMethod(MethodSpec.methodBuilder("asImmutable")
      .addJavadoc("Returns this message as an immutable message, without any copies.")
      .addModifiers(Modifier.PUBLIC)
      .returns(info.typeName)
      .addStatement("return this")
      .build
    )

  private def generateEquals(t: TypeSpec.Builder): Unit =
    val equals = MethodSpec.methodBuilder("equals")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(classOf[Boolean])
      .addParameter(classOf[AnyRef], "o")
    // Reference equality check
    equals.beginControlFlow("if (o == this)").addStatement("return true").endControlFlow
    // Type check
    equals.beginControlFlow("if (!(o instanceof $T))", info.typeName)
      .addStatement("return false")
      .endControlFlow
    equals.addStatement("$1T other = ($1T) o", info.typeName)
    // Check whether all of the same fields are set
    if (info.fieldCount > 0) {
      equals.addCode("return $>")
      var i = 0
      for field <- fields do
        if i > 0 then equals.addCode("\n&& ")
        field.generateEqualsStatement(equals)
        i += 1
      for oneOf <- oneOfGenerators do
        if i > 0 then equals.addCode("\n&& ")
        oneOf.generateEqualsStatement(equals)
        i += 1
      equals.addCode(";$<\n")
    }
    else equals.addCode("return true;\n")
    t.addMethod(equals.build)

  private def generateMergeFrom(t: TypeSpec.Builder): Unit =
    val mergeFrom = MethodSpec.methodBuilder("mergeFrom")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(info.mutableTypeName)
      .addParameter(RuntimeClasses.LimitedCodedInputStream, "inputLimited", Modifier.FINAL)
      .addException(classOf[IOException])
    // Fallthrough optimization:
    //
    // Reads tag after case parser and checks if it can fall-through. In the ideal case if all fields are set
    // and the expected order matches the incoming data, the switch would only need to be executed once
    // for the first field.
    //
    // Packable fields make this a bit more complex since they need to generate two cases to preserve
    // backwards compatibility. However, any production proto file should already be using the packed
    // option whenever possible, so we don't need to optimize the non-packed case.
    val enableFallthroughOptimization = true
    // Interleave the oneof fields. In Jelly-RDF, this optimizes for the case where s, p, o, g are
    // all RdfIri messages.
    val sortedFields = fields.sortBy(_.info.number) ++
      oneOfGenerators.flatMap(oneOf => oneOf.fieldGenerators.zipWithIndex)
      .sortBy(_._2)
      .map(_._1)
    if (enableFallthroughOptimization) {
      mergeFrom.addComment("Enabled Fall-Through Optimization")
      mergeFrom.addAnnotation(AnnotationSpec
        .builder(classOf[SuppressWarnings])
        .addMember("value", "$S", "fallthrough")
        .build
      )
    }
    mergeFrom.addStatement("final $T input = inputLimited.in()", RuntimeClasses.CodedInputStream)
      .addStatement(named("int tag = input.readTag()"))

    // If this is an empty message, we can skip the whole switch statement
    if info.isEmptyMessage then
      mergeFrom
        .addStatement("input.skipField(tag)")
        .addStatement("return this")
      t.addMethod(mergeFrom.build)
      return
    mergeFrom
      .beginControlFlow("while (true)")
      .beginControlFlow("switch (tag)")
    // Add fields by the expected order and type
    for (i <- sortedFields.indices) {
      val field = sortedFields(i)
      val maybeOneOf = oneOfGenerators.find(_.fields.contains(field.info))
      // Assume all packable fields are written packed. Add non-packed cases to the end.
      var readTag = true
      if (field.info.isPackable) {
        mergeFrom.beginControlFlow("case $L:", field.info.packedTag)
        mergeFrom.addComment("$L [packed=true]", field.info.fieldName)
        readTag = field.generateMergingCodeFromPacked(mergeFrom)
      }
      else {
        mergeFrom.beginControlFlow("case $L:", field.info.tag)
        mergeFrom.addComment("$L", field.info.fieldName)
        readTag = maybeOneOf match
          case Some(oneOf) => oneOf.generateMergingCode(mergeFrom, field)
          case None => field.generateMergingCode(mergeFrom)
      }
      if (readTag) mergeFrom.addCode(named("tag = input.readTag();\n"))
      if (enableFallthroughOptimization) {
        // try falling to 0 (exit) at last field
        val nextCase = if (i == sortedFields.size - 1) 0
        else getPackedTagOrTag(sortedFields(i + 1))
        mergeFrom.beginControlFlow("if (tag != $L)", nextCase)
        mergeFrom.addStatement("break")
        mergeFrom.endControlFlow
      }
      else mergeFrom.addStatement("break")
      mergeFrom.endControlFlow
    }
    // zero means invalid tag / end of data
    mergeFrom.beginControlFlow("case 0:").addStatement("return this").endControlFlow
    // default case -> skip field
    val ifSkipField = named("if (!input.skipField(tag))")
    mergeFrom.beginControlFlow("default:").beginControlFlow(ifSkipField).addStatement("return this")
    mergeFrom.endControlFlow.addStatement(named("tag = input.readTag()")).addStatement("break").endControlFlow
    // Generate missing non-packed cases for packable fields for compatibility reasons
    for (field <- sortedFields) {
      if (field.info.isPackable) {
        mergeFrom.beginControlFlow("case $L:", field.info.tag)
        mergeFrom.addComment("$L [packed=false]", field.info.fieldName)
        val readTag = field.generateMergingCode(mergeFrom)
        if (readTag) mergeFrom.addCode(named("tag = input.readTag();\n"))
        mergeFrom.addStatement("break").endControlFlow
      }
    }
    mergeFrom.endControlFlow
    mergeFrom.endControlFlow
    t.addMethod(mergeFrom.build)

  private def getPackedTagOrTag(field: FieldGenerator): Int =
    if (field.info.isPackable) return field.info.packedTag
    field.info.tag

  private def generateWriteTo(t: TypeSpec.Builder): Unit =
    val writeTo = MethodSpec.methodBuilder("writeTo")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(classOf[Unit])
      .addParameter(RuntimeClasses.CodedOutputStream, "output", Modifier.FINAL)
      .addException(classOf[IOException])
    fields.foreach(f => {
      val checker = CodeBlock.builder().add("if (")
      f.generateHasChecker(checker)
      writeTo.beginControlFlow(checker.add(")").build())
      f.generateSerializationCode(writeTo)
      writeTo.endControlFlow
    })
    oneOfGenerators.foreach(_.generateWriteToCode(writeTo))
    t.addMethod(writeTo.build)

  private def generateSerializedSize(t: TypeSpec.Builder): Unit =
    val cachedSize = FieldSpec.builder(classOf[Int], "cachedSize")
      .addModifiers(Modifier.PROTECTED)
      .initializer("-1")
      .build
    t.addField(cachedSize)
    val getSerializedSize = MethodSpec.methodBuilder("getSerializedSize")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(classOf[Int])
      .addCode(CodeBlock.builder()
        .beginControlFlow("if (cachedSize < 0)")
        .addStatement("cachedSize = computeSerializedSize()")
        .endControlFlow
        .addStatement("return cachedSize")
        .build
      )
    t.addMethod(getSerializedSize.build)
    val computeSerializedSize = MethodSpec.methodBuilder("computeSerializedSize")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PROTECTED, Modifier.FINAL)
      .returns(classOf[Int])
    if info.isEmptyMessage then
      computeSerializedSize.addStatement("return 0")
    else
      // Check all required fields at once
      computeSerializedSize.addStatement("int size = 0")
      fields.foreach(f => {
        val checker = CodeBlock.builder().add("if (")
        f.generateHasChecker(checker)
        computeSerializedSize.beginControlFlow(checker.add(")").build())
        f.generateComputeSerializedSizeCode(computeSerializedSize)
        computeSerializedSize.endControlFlow
      })
      oneOfGenerators.foreach(_.generateComputeSerializedSizeCode(computeSerializedSize))
      computeSerializedSize.addStatement("return size")
    t.addMethod(computeSerializedSize.build)
    val getCached = MethodSpec.methodBuilder("getCachedSize")
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(classOf[Int])
      .addStatement("return cachedSize")
    t.addMethod(getCached.build)
    val resetSize = MethodSpec.methodBuilder("resetCachedSize")
      .addJavadoc("Resets the cached size of this message.\n" +
        "Call this method if you modify the message after it was serialized.\n" +
        "NOTE: this is a SHALLOW operation! It will not reset the size of nested messages."
      )
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(classOf[Unit])
      .addStatement("cachedSize = -1")
    t.addMethod(resetSize.build)

  private def generateCopyFrom(t: TypeSpec.Builder): Unit =
    val copyFrom = MethodSpec.methodBuilder("copyFrom")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addParameter(info.typeName, "other", Modifier.FINAL)
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(info.mutableTypeName)
    copyFrom.addStatement("cachedSize = other.cachedSize")
    fields.foreach(_.generateCopyFromCode(copyFrom))
    oneOfGenerators.foreach(_.generateCopyFromCode(copyFrom))
    copyFrom.addStatement("return this")
    t.addMethod(copyFrom.build)

  private def generateMergeFromMessage(t: TypeSpec.Builder): Unit =
    val mergeFrom = MethodSpec.methodBuilder("mergeFrom")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addParameter(info.typeName, "other", Modifier.FINAL)
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(info.mutableTypeName)
    mergeFrom.addStatement("cachedSize = -1")
    fields.foreach(_.generateMergeFromMessageCode(mergeFrom))
    oneOfGenerators.foreach(_.generateMergeFromMessageCode(mergeFrom))
    mergeFrom.addStatement("return this")
    t.addMethod(mergeFrom.build)

  private def generateClear(builder: TypeSpec.Builder): Unit =
    val clear = MethodSpec.methodBuilder("clear")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(info.mutableTypeName)
    fields.foreach(_.generateClearCode(clear))
    oneOfGenerators.foreach(_.generateClearCode(clear))
    clear.addStatement("cachedSize = -1")
    clear.addStatement("return this")
    builder.addMethod(clear.build)

  private def generateClone(t: TypeSpec.Builder): Unit =
    t.addSuperinterface(classOf[Cloneable])
    t.addMethod(MethodSpec.methodBuilder("clone")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(info.mutableTypeName)
      .addStatement("return newInstance().copyFrom(this)")
      .build
    )

  private def generateParseFrom(t: TypeSpec.Builder): Unit =
    t.addMethod(MethodSpec.methodBuilder("parseFrom")
      .addJavadoc(
        "Parse this message in NON-delimited form from a byte array.\n" +
        "This assumes that the message spans the entire array."
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .addException(RuntimeClasses.InvalidProtocolBufferException)
      .addParameter(classOf[Array[Byte]], "data", Modifier.FINAL)
      .returns(info.typeName)
      .addStatement("return $T.mergeFrom(newInstance(), data)", RuntimeClasses.AbstractMessage)
      .build
    )
    t.addMethod(MethodSpec.methodBuilder("parseFrom")
      .addJavadoc(
        "Parse this message in NON-delimited form from a {@link $T}.\n" +
        "This assumes that the message spans the entire input stream.",
        RuntimeClasses.LimitedCodedInputStream
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .addException(classOf[IOException])
      .addParameter(RuntimeClasses.LimitedCodedInputStream, "input", Modifier.FINAL)
      .returns(info.typeName)
      .addStatement("return $T.mergeFrom(newInstance(), input)", RuntimeClasses.AbstractMessage)
      .build
    )
    t.addMethod(MethodSpec.methodBuilder("parseFrom")
      .addJavadoc(
        "Parse this message in NON-delimited form from a Java {@link $T}.\n" +
        "This assumes that the message spans the entire input stream.",
        classOf[java.io.InputStream]
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .addException(classOf[IOException])
      .addParameter(classOf[java.io.InputStream], "input", Modifier.FINAL)
      .returns(info.typeName)
      .addStatement(
        "return $T.parseFrom(input, $T.getFactory())",
        RuntimeClasses.AbstractMessage,
        info.typeName
      )
      .build
    )
    t.addMethod(MethodSpec.methodBuilder("parseDelimitedFrom")
      .addJavadoc(
        "Parse this message in DELIMITED form from a Java {@link $T}.\n" +
        "If there is no message to be read, null will be returned.\n" +
        "To read all delimited messages in the stream, call this method\n" +
        "repeatedly until null is returned.",
        classOf[java.io.InputStream]
      )
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .addException(classOf[IOException])
      .addParameter(classOf[java.io.InputStream], "input", Modifier.FINAL)
      .returns(info.typeName)
      .addStatement(
        "return $T.parseDelimitedFrom(input, $T.getFactory())",
        RuntimeClasses.AbstractMessage,
        info.typeName
      )
      .build
    )

  private def generateMessageFactory(t: TypeSpec.Builder): Unit =
    val factoryReturnType = ParameterizedTypeName.get(RuntimeClasses.MessageFactory, info.typeName)
    val factoryTypeName = info.typeName.nestedClass(info.typeName.simpleName + "Factory")
    val factoryMethod = MethodSpec.methodBuilder("create")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
      .returns(info.typeName)
      .addStatement("return $T.newInstance()", info.typeName)
      .build
    val factoryEnum = TypeSpec.enumBuilder(factoryTypeName.simpleName)
      .addModifiers(Modifier.PRIVATE)
      .addSuperinterface(factoryReturnType)
      .addEnumConstant("INSTANCE")
      .addMethod(factoryMethod)
      .build
    t.addType(factoryEnum)
    t.addMethod(MethodSpec.methodBuilder("getFactory")
      .addJavadoc("@return factory for creating $T messages", info.typeName)
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(factoryReturnType)
      .addStatement("return $T.INSTANCE", factoryTypeName)
      .build
    )

  private def generateDescriptors(t: TypeSpec.Builder): Unit =
    val descriptorClass = info.parentFile.outerClassName
    val fieldName = DescriptorGenerator.getDescriptorFieldName(info)
    t.addMethod(MethodSpec.methodBuilder("getDescriptor")
      .addJavadoc("@return this type's descriptor.")
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(RuntimeClasses.MessageDescriptor)
      .addStatement("return $T.$N", descriptorClass, fieldName)
      .build
    )

  private def named(format: String, args: AnyRef*) =
    CodeBlock.builder.addNamed(format, m).build
