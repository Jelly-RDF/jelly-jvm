package eu.neverblink.protoc.java.gen

import com.palantir.javapoet.*
import eu.neverblink.protoc.java.gen.RequestInfo.{EnumInfo, EnumValueInfo}

import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.*
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

/** @author
  *   Florian Enner
  * @author
  *   Piotr Sowiński
  */
object EnumGenerator:
  private def getFieldName(value: EnumValueInfo) = NamingUtil.filterKeyword(value.getName)

  private def getValueFieldName(value: EnumValueInfo) = value.getName + "_VALUE"

class EnumGenerator(val info: EnumInfo):
  private val converterClass = info.typeName.nestedClass(info.typeName.simpleName + "Converter")
  private val converterInterface =
    ParameterizedTypeName.get(RuntimeClasses.EnumConverter, info.typeName)

  def generate: TypeSpec =
    val t = TypeSpec.enumBuilder(info.typeName)
      .addJavadoc(Javadoc.forEnum(info))
      .addSuperinterface(ParameterizedTypeName.get(RuntimeClasses.ProtoEnum, info.typeName))
      .addModifiers(PUBLIC)
    // Add enum constants
    for (value <- info.values.asScala) {
      val name = value.getName
      t.addEnumConstant(
        EnumGenerator.getFieldName(value),
        TypeSpec.anonymousClassBuilder("$S, $L", name, value.getNumber)
          .addJavadoc(Javadoc.forEnumValue(value))
          .build,
      )
      t.addField(
        FieldSpec.builder(
          classOf[Int],
          EnumGenerator.getValueFieldName(value),
          PUBLIC,
          STATIC,
          FINAL,
        )
          .initializer("$L", value.getNumber)
          .addJavadoc(Javadoc.forEnumValue(value))
          .build,
      )
    }
    // Add alias constants
    for (alias <- info.aliases.asScala) {
      val value = info.findAliasedValue(alias)
      t.addField(
        FieldSpec
          .builder(info.typeName, EnumGenerator.getFieldName(alias), PUBLIC, STATIC, FINAL)
          .initializer("$L", EnumGenerator.getFieldName(value))
          .addJavadoc(Javadoc.forEnumValue(value))
          .build,
      )
      t.addField(
        FieldSpec
          .builder(classOf[Int], EnumGenerator.getValueFieldName(alias), PUBLIC, STATIC, FINAL)
          .initializer("$L", EnumGenerator.getValueFieldName(value))
          .addJavadoc(Javadoc.forEnumValue(value))
          .build,
      )
    }
    generateProtoEnumInterface(t)
    generateConstructor(t)
    generateStaticMethods(t)
    generateConverter(t)
    t.build

  private def generateConstructor(typeSpec: TypeSpec.Builder): Unit =
    typeSpec.addMethod(
      MethodSpec.constructorBuilder
        .addModifiers(Modifier.PRIVATE)
        .addParameter(classOf[String], "name")
        .addParameter(classOf[Int], "number")
        .addStatement("this.$1N = $1N", "name")
        .addStatement("this.$1N = $1N", "number")
        .build,
    )
    typeSpec.addField(FieldSpec.builder(classOf[String], "name", Modifier.PRIVATE, FINAL).build)
    typeSpec.addField(FieldSpec.builder(classOf[Int], "number", Modifier.PRIVATE, FINAL).build)

  private def generateProtoEnumInterface(typeSpec: TypeSpec.Builder): Unit =
    typeSpec.addMethod(
      MethodSpec.methodBuilder("getName")
        .addJavadoc("@return the string representation of enum entry")
        .addAnnotation(classOf[Override])
        .addModifiers(PUBLIC)
        .returns(classOf[String])
        .addStatement("return name")
        .build,
    )
    typeSpec.addMethod(
      MethodSpec.methodBuilder("getNumber")
        .addJavadoc("@return the numeric wire value of this enum entry")
        .addAnnotation(classOf[Override])
        .addModifiers(PUBLIC)
        .returns(classOf[Int])
        .addStatement("return number")
        .build,
    )

  private def generateStaticMethods(typeSpec: TypeSpec.Builder): Unit =
    typeSpec.addMethod(
      MethodSpec.methodBuilder("converter")
        .addJavadoc(
          "@return a converter that maps between this enum's numeric and text representations",
        )
        .addModifiers(PUBLIC, STATIC)
        .returns(converterInterface)
        .addStatement("return $T.INSTANCE", converterClass)
        .build,
    )
    typeSpec.addMethod(
      MethodSpec.methodBuilder("forNumber")
        .addJavadoc(
          "@param value The numeric wire value of the corresponding enum entry.\n" +
            "@return The enum associated with the given numeric wire value, or null if unknown.",
        )
        .addModifiers(PUBLIC, STATIC)
        .returns(info.typeName)
        .addParameter(TypeName.INT, "value")
        .addStatement("return $T.INSTANCE.forNumber(value)", converterClass)
        .build,
    )
    typeSpec.addMethod(
      MethodSpec.methodBuilder("forNumberOr")
        .addJavadoc(
          "" +
            "@param number The numeric wire value of the corresponding enum entry.\n" +
            "@param other Fallback value in case the value is not known.\n" +
            "@return The enum associated with the given numeric wire value, or the fallback value if unknown.",
        )
        .addModifiers(PUBLIC, STATIC)
        .returns(info.typeName)
        .addParameter(classOf[Int], "number")
        .addParameter(info.typeName, "other")
        .addStatement("$T value = forNumber(number)", info.typeName)
        .addStatement("return value == null ? other : value")
        .build,
    )

  private def generateConverter(typeSpec: TypeSpec.Builder): Unit =
    val decoder = TypeSpec.enumBuilder(converterClass)
      .addSuperinterface(converterInterface)
      .addEnumConstant("INSTANCE")
    // Number to Enum
    val forNumber = MethodSpec.methodBuilder("forNumber")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(PUBLIC, FINAL)
      .returns(info.typeName)
      .addParameter(TypeName.INT, "value", FINAL)
    if (info.usingArrayLookup) {
      // (fast) lookup using array index
      forNumber.beginControlFlow("if (value >= 0 && value < lookup.length)")
        .addStatement("return lookup[value]")
        .endControlFlow
        .addStatement("return null")
      val arrayType = ArrayTypeName.of(info.typeName)
      decoder.addField(
        FieldSpec
          .builder(arrayType, "lookup", Modifier.PRIVATE, STATIC, FINAL)
          .initializer("new $T[$L]", info.typeName, info.highestNumber + 1)
          .build,
      )
      val initBlock = CodeBlock.builder
      for (value <- info.values.asScala) {
        initBlock.addStatement(
          "lookup[$L] = $L",
          value.getNumber,
          NamingUtil.filterKeyword(value.getName),
        )
      }
      decoder.addStaticBlock(initBlock.build)
    } else {
      // lookup using switch statement
      forNumber.beginControlFlow("switch(value)")
      for (value <- info.values.asScala) {
        forNumber.addStatement(
          "case $L: return $L",
          value.getNumber,
          NamingUtil.filterKeyword(value.getName),
        )
      }
      forNumber.addStatement("default: return null")
      forNumber.endControlFlow
    }
    decoder.addMethod(forNumber.build)
    // Name to Enum
    val forName = MethodSpec.methodBuilder("forName")
      .addJavadoc(Javadoc.inherit)
      .addAnnotation(classOf[Override])
      .addModifiers(PUBLIC, FINAL)
      .returns(info.typeName)
      .addParameter(classOf[CharSequence], "value", FINAL)
    val cases = info.values.stream
      .map(_.getName)
      .mapToInt(_.length)
      .distinct
      .sorted
      .toArray
    val createCaseIfs = (len: Int) => {
      info.values.stream.filter(value => value.getName.length eq len).forEach(value => {
        forName
          .beginControlFlow("if ($S == value)", value.getName)
          .addStatement("return $N", NamingUtil.filterKeyword(value.getName))
          .endControlFlow
      })
    }
    if (cases.length <= 3) {
      // check length brackets
      for (len <- cases) {
        forName.beginControlFlow("if (value.length() == $L)", len)
        createCaseIfs(len)
        forName.endControlFlow
      }
    } else {
      // switch lookup on length
      forName.beginControlFlow("switch (value.length())")
      for (len <- cases) {
        forName.beginControlFlow("case $L:", len)
        createCaseIfs(len)
        forName.addStatement("break").endControlFlow
      }
      forName.endControlFlow
    }
    forName.addStatement("return null")
    decoder.addMethod(forName.build)
    typeSpec.addType(decoder.build)
