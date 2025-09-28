package eu.neverblink.protoc.java.gen

import com.palantir.javapoet.{ClassName, FieldSpec, MethodSpec, TypeName, TypeSpec}
import eu.neverblink.protoc.java.gen.RequestInfo.OneOfInfo

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

/** @author
  *   Florian Enner
  * @author
  *   Piotr Sowiński
  */
class OneOfGenerator(val info: OneOfInfo):
  val fields: Seq[RequestInfo.FieldInfo] = info.getFields
  val fieldGenerators: Seq[FieldGenerator] = fields.map(FieldGenerator(_))

  def generateMemberFields(t: TypeSpec.Builder): Unit =
    val field = FieldSpec.builder(RuntimeClasses.ObjectType, info.fieldName)
      .addJavadoc(Javadoc.forOneOfField(info).build)
      .addModifiers(Modifier.PROTECTED)
    field.initializer("null")
    t.addField(field.build)
    // Class-based oneofs do not need a field for the number, we match by class
    if !info.usesClassBasedOneof then
      val numberField = FieldSpec.builder(TypeName.BYTE, info.numberFieldName)
        .addModifiers(Modifier.PROTECTED)
        .initializer("$L", 0)
      t.addField(numberField.build)

  def generateMemberMethods(t: TypeSpec.Builder, tMutable: TypeSpec.Builder): Unit =
    // Checks if any has state is true
    val has = MethodSpec.methodBuilder(info.hazzerName)
      .addModifiers(Modifier.PUBLIC)
      .returns(classOf[Boolean])
    if info.usesClassBasedOneof then has.addStatement("return $N != null", info.fieldName)
    else has.addStatement("return $N != 0", info.numberFieldName)
    t.addMethod(has.build)
    // Set the value -- general method
    val set = MethodSpec.methodBuilder(info.setterName)
      .addJavadoc(
        "Low-level setter for the <code>$L</code> oneof field.\n" +
          "Use with care, as it will not check the type of the value.",
        info.descriptor.getName,
      )
      .addModifiers(Modifier.PUBLIC)
      .returns(info.parentTypeInfo.mutableTypeName)
      .addParameter(RuntimeClasses.ObjectType, info.fieldName)
      .addStatement("this.$N = $N", info.fieldName, info.fieldName)
    if !info.usesClassBasedOneof then
      set.addParameter(TypeName.BYTE, "number")
      set.addStatement("this.$N = $L", info.numberFieldName, "number")
    set.addStatement("return this")
    tMutable.addMethod(set.build)
    // Get the value -- general method
    val get = MethodSpec.methodBuilder(info.getterName)
      .addJavadoc("Returns the <code>$L</code> oneof field.", info.descriptor.getName)
      .addModifiers(Modifier.PUBLIC)
      .returns(RuntimeClasses.ObjectType)
      .addStatement("return $N", info.fieldName)
    t.addMethod(get.build)
    if !info.usesClassBasedOneof then
      // Get the value -- field number method
      val getNumber = MethodSpec.methodBuilder(info.getNumberName)
        .addJavadoc(
          "Returns the set field number of the <code>$L</code> oneof field.",
          info.descriptor.getName,
        )
        .addModifiers(Modifier.PUBLIC)
        .returns(TypeName.BYTE)
        .addStatement("return $N", info.numberFieldName)
      t.addMethod(getNumber.build)
      // Specific per-field methods
      for field <- fields do
        // Set
        val setField = MethodSpec.methodBuilder(field.setterName)
          .addJavadoc(
            "Sets the <code>$L</code> oneof field to $L.",
            info.descriptor.getName,
            field.fieldName,
          )
          .addModifiers(Modifier.PUBLIC)
          .returns(info.parentTypeInfo.mutableTypeName)
          .addParameter(field.getTypeName, field.fieldName)
          .addStatement("this.$N = $N", info.fieldName, field.fieldName)
          .addStatement("this.$N = $L", info.numberFieldName, field.descriptor.getNumber)
          .addStatement("return this")
        tMutable.addMethod(setField.build)
        // Get
        val getField = MethodSpec.methodBuilder(field.getterName)
          .addJavadoc(
            "Returns the <code>$L</code> oneof field.\n" +
              "Use with care, as it will not check if the correct field number is actually set.",
            info.descriptor.getName,
          )
          .addModifiers(Modifier.PUBLIC)
          .returns(field.getTypeName)
          .addStatement("return ($T) $N", field.getTypeName, info.fieldName)
        t.addMethod(getField.build)
        // Has
        val hasField = MethodSpec.methodBuilder(field.hazzerName)
          .addJavadoc(
            "Checks if the <code>$L</code> oneof is set to $L.",
            info.descriptor.getName,
            field.fieldName,
          )
          .addModifiers(Modifier.PUBLIC)
          .returns(classOf[Boolean])
          .addStatement("return $N == $L", info.numberFieldName, field.descriptor.getNumber)
        t.addMethod(hasField.build)

  def generateCopyFromCode(method: MethodSpec.Builder): Unit =
    method.addStatement("this.$N = other.$N", info.fieldName, info.fieldName)
    if !info.usesClassBasedOneof then
      method.addStatement("this.$N = other.$N", info.numberFieldName, info.numberFieldName)

  def generateMergeFromMessageCode(method: MethodSpec.Builder): Unit =
    // Not an actual merge, we just set the value
    generateCopyFromCode(method)

  def generateEqualsStatement(method: MethodSpec.Builder): Unit = {
    if info.usesClassBasedOneof then
      method.addCode(
        "($N == null && other.$N == null || $N != null && $N.equals(other.$N))",
        info.fieldName,
        info.fieldName,
        info.fieldName,
        info.fieldName,
        info.fieldName,
      )
    else
      method.addCode(
        "$N == other.$N && ($N == 0 || $N.equals(other.$N))",
        info.numberFieldName,
        info.numberFieldName,
        info.numberFieldName,
        info.fieldName,
        info.fieldName,
      )
  }

  def generateClearCode(method: MethodSpec.Builder): Unit =
    if info.usesClassBasedOneof then method.addStatement("this.$N = null", info.fieldName)
    else method.addStatement("this.$N = 0", info.numberFieldName)

  def generateWriteToCode(method: MethodSpec.Builder): Unit =
    if info.usesClassBasedOneof then
      for (f, ix) <- fieldGenerators.zipWithIndex do
        serializationByClassBranch(method, f, ix)
        f.generateSerializationCode(method)
      method.endControlFlow
    else
      method.beginControlFlow("switch ($N)", info.numberFieldName)
      for f <- fieldGenerators do
        method.beginControlFlow("case $L:", f.info.descriptor.getNumber)
        if !f.info.isEmptyMessage then
          method.addStatement("final var $N = $N()", f.info.fieldName, f.info.getterName)
        f.generateSerializationCode(method)
        method.addStatement("break")
        method.endControlFlow
      method.endControlFlow

  def generateComputeSerializedSizeCode(method: MethodSpec.Builder): Unit =
    if info.usesClassBasedOneof then
      for (f, ix) <- fieldGenerators.zipWithIndex do
        serializationByClassBranch(method, f, ix)
        f.generateComputeSerializedSizeCode(method)
      method.endControlFlow
    else
      method.beginControlFlow("switch ($N)", info.numberFieldName)
      for f <- fieldGenerators do
        method.beginControlFlow("case $L:", f.info.descriptor.getNumber)
        if !f.info.isEmptyMessage then
          method.addStatement("final var $N = $N()", f.info.fieldName, f.info.getterName)
        f.generateComputeSerializedSizeCode(method)
        method.addStatement("break")
        method.endControlFlow
      method.endControlFlow

  private def serializationByClassBranch(
      method: MethodSpec.Builder,
      f: FieldGenerator,
      ix: Int,
  ): MethodSpec.Builder =
    // Compare against final subclass type for better performance
    val typeName =
      if f.info.isPrimitive || f.info.isString then f.info.getTypeName
      else f.info.getTypeName.asInstanceOf[ClassName].nestedClass("Mutable")
    val extractor = "$N instanceof $T $N"
    if ix == 0 then
      method.beginControlFlow(f"if ($extractor)", info.fieldName, typeName, f.info.fieldName)
    else method.nextControlFlow(f"else if ($extractor)", info.fieldName, typeName, f.info.fieldName)

  /** @return
    *   true if the tag needs to be read
    */
  def generateMergingCode(
      method: MethodSpec.Builder,
      field: FieldGenerator,
      fastOneof: Boolean,
  ): Boolean =
    if info.usesClassBasedOneof then generateMergingCodeByClass(method, field)
    else generateMergingCodeByNumber(method, field, fastOneof)

  private def generateMergingCodeByClass(
      method: MethodSpec.Builder,
      field: FieldGenerator,
  ): Boolean =
    if field.info.isEmptyMessage then
      // We simply set the value to the singleton instance
      method
        .addStatement("$N($T.EMPTY)", info.setterName, field.info.getTypeName)
        .addStatement("input.skipField(tag)")
    else if field.info.isMessage then
      // If the field is already set to the same kind of message, we merge it.
      // Otherwise, we create a new instance of the message and merge it.
      method
        .addStatement("final $T $N", field.info.getTypeName, field.info.fieldName)
        .beginControlFlow(
          "if ($N != null && $N instanceof $T __v)",
          info.fieldName,
          info.fieldName,
          field.info.getTypeName.asInstanceOf[ClassName].nestedClass("Mutable"),
        )
        .addStatement("$N = __v", field.info.fieldName)
        .nextControlFlow("else")
        .addStatement("$N = $T.newInstance()", field.info.fieldName, field.info.getTypeName)
        .addStatement("$N($N)", info.setterName, field.info.fieldName)
        .endControlFlow
      generateMergeDelimitedFromCall(method, field)
    else if field.info.isString then
      method.addStatement("$N(input.readStringRequireUtf8())", info.setterName)
    else if field.info.isPrimitive then
      method.addStatement(
        "$N(input.read$L())",
        info.setterName,
        FieldUtil.getCapitalizedType(field.info.descriptor.getType),
      )
    else throw new IllegalStateException("Unhandled field type: " + field.info.getTypeName)
    true

  private def generateMergingCodeByNumber(
      method: MethodSpec.Builder,
      field: FieldGenerator,
      fastOneof: Boolean,
  ): Boolean =
    if field.info.isEmptyMessage then
      // We simply set the correct field number and the value to the singleton instance
      method
        .addStatement("$N($T.EMPTY)", field.info.setterName, field.info.getTypeName)
        .addStatement("input.skipField(tag)")
    else if field.info.isMessage then
      if fastOneof then
        method
          .addStatement(
            "final $T $N = $T.newInstance()",
            field.info.getTypeName,
            field.info.fieldName,
            field.info.getTypeName,
          )
          .addStatement("$N($N)", field.info.setterName, field.info.fieldName)
        generateMergeDelimitedFromCall(method, field)
      else
        // If the field is already set to the same kind of message, we merge it.
        // Otherwise, we create a new instance of the message and merge it.
        method
          .addStatement("final $T $N", field.info.getTypeName, field.info.fieldName)
          .beginControlFlow("if ($N == $L)", info.numberFieldName, field.info.descriptor.getNumber)
          .addStatement("$N = $N()", field.info.fieldName, field.info.getterName)
          .nextControlFlow("else")
          .addStatement("$N = $T.newInstance()", field.info.fieldName, field.info.getTypeName)
          .addStatement("$N($N)", field.info.setterName, field.info.fieldName)
          .endControlFlow
        generateMergeDelimitedFromCall(method, field)
    else if field.info.isString then
      method.addStatement("$N(input.readStringRequireUtf8())", field.info.setterName)
    else if field.info.isPrimitive then
      method.addStatement(
        "$N(input.read$L())",
        field.info.setterName,
        FieldUtil.getCapitalizedType(field.info.descriptor.getType),
      )
    else throw new IllegalStateException("Unhandled field type: " + field.info.getTypeName)
    true

  private def generateMergeDelimitedFromCall(
      method: MethodSpec.Builder,
      field: FieldGenerator,
  ): Unit =
    val typeName = field.info.getTypeName.asInstanceOf[ClassName].simpleName()
    if info.parentTypeInfo.request.pluginOptions.isRecursive(typeName) then
      method
        .beginControlFlow("if (remainingDepth < 0)")
        .addStatement("throw new RuntimeException(\"Maximum recursion depth exceeded\")")
        .endControlFlow
    method.addStatement(
      "ProtoMessage.mergeDelimitedFrom($N, input, remainingDepth)",
      field.info.fieldName,
    )

  def generateConstants(t: TypeSpec.Builder): Unit =
    for field <- fields do
      val constant = FieldSpec.builder(TypeName.BYTE, NamingUtil.getConstantName(field.fieldName))
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
        .initializer("$L", field.descriptor.getNumber)
      t.addField(constant.build)
