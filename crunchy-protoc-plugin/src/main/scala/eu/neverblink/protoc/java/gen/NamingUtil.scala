package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos

import java.io.File
import java.util
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

/** Utilities for dealing with names
  *
  * @author
  *   Florian Enner
  * @author
  *   Piotr Sowiński
  */
object NamingUtil:
  def getJavaPackage(descriptor: DescriptorProtos.FileDescriptorProto): String =
    if (descriptor.getOptions.hasJavaPackage) return descriptor.getOptions.getJavaPackage
    getProtoPackage(descriptor)

  def getProtoPackage(descriptor: DescriptorProtos.FileDescriptorProto): String =
    if (descriptor.hasPackage) return descriptor.getPackage
    DEFAULT_PACKAGE

  def getJavaOuterClassname(descriptor: DescriptorProtos.FileDescriptorProto): String =
    if (descriptor.getOptions.hasJavaOuterClassname)
      return descriptor.getOptions.getJavaOuterClassname
    val nameWithoutPath = new File(descriptor.getName).getName // removes slashes etc.

    val defaultOuterClassName = toUpperCamel(stripSuffixString(nameWithoutPath))
    // add suffix on collisions to match gen-java behavior
    if (!hasConflictingClassName(descriptor, defaultOuterClassName)) return defaultOuterClassName
    defaultOuterClassName + OUTER_CLASS_SUFFIX

  private def hasConflictingClassName(
      descriptor: DescriptorProtos.FileDescriptorProto,
      outerClassName: String,
  ): Boolean =
    descriptor.getMessageTypeList.asScala
      .map(m => toUpperCamel(m.getName))
      .contains(outerClassName) ||
      descriptor.getEnumTypeList.asScala
        .map(e => toUpperCamel(e.getName))
        .contains(outerClassName)
    false

  private def stripSuffixString(fileName: String): String =
    if (fileName.endsWith(".proto")) return fileName.substring(0, fileName.length - ".proto".length)
    if (fileName.endsWith(".protodevel"))
      return fileName.substring(0, fileName.length - ".protodevel".length)
    fileName

  private val DEFAULT_PACKAGE = ""
  private val OUTER_CLASS_SUFFIX = "OuterClass"

  def toUpperCamel(name: String): String = underscoresToCamelCaseImpl(name, true)

  /** Port of JavaNano's "UnderscoresToCamelCaseImpl". Guava's CaseFormat doesn't write upper case
    * after numbers, so the names wouldn't be consistent.
    *
    * @param input
    *   original name with lower_underscore case
    * @param capFirstLetter
    *   true if the first letter should be capitalized
    * @return
    *   camelCase
    */
  private def underscoresToCamelCaseImpl(input: CharSequence, capFirstLetter: Boolean) =
    val result = new StringBuilder(input.length)
    var cap_next_letter = capFirstLetter
    for (i <- 0 until input.length) {
      val c = input.charAt(i)
      if ('a' <= c && c <= 'z') {
        if (cap_next_letter) result.append(Character.toUpperCase(c))
        else result.append(c)
        cap_next_letter = false
      } else if ('A' <= c && c <= 'Z') {
        if (i == 0 && !cap_next_letter) {
          // Force first letter to lower-case unless explicitly told to
          // capitalize it.
          result.append(Character.toLowerCase(c))
        } else {
          // Capital letters after the first are left as-is.
          result.append(c)
        }
        cap_next_letter = false
      } else if ('0' <= c && c <= '9') {
        result.append(c)
        cap_next_letter = true
      } else cap_next_letter = true
    }
    result.toString

  def getConstantName(camelCase: String): String =
    // convert camelCase to UPPER_UNDERSCORE
    val result = new StringBuilder(camelCase.length)
    for (i <- 0 until camelCase.length) {
      val c = camelCase.charAt(i)
      if ('a' <= c && c <= 'z') result.append(Character.toUpperCase(c))
      else if ('A' <= c && c <= 'Z') {
        if (i > 0) result.append('_')
        result.append(c)
      } else if ('0' <= c && c <= '9') result.append(c)
      else result.append('_')
    }
    result.toString

  def filterKeyword(name: String): String =
    if (keywordSet.contains(name)) name + "_" else name

  def isCollidingFieldName(field: String): Boolean =
    collidingFieldSet.contains(field)

  private val keywordSet = new util.HashSet[String](
    util.Arrays.asList(
      // Reserved Java Keywords
      "abstract",
      "assert",
      "boolean",
      "break",
      "byte",
      "case",
      "catch",
      "char",
      "class",
      "const",
      "continue",
      "default",
      "do",
      "double",
      "else",
      "enum",
      "extends",
      "final",
      "finally",
      "float",
      "for",
      "goto",
      "if",
      "implements",
      "import",
      "instanceof",
      "int",
      "interface",
      "long",
      "native",
      "new",
      "package",
      "private",
      "protected",
      "public",
      "return",
      "short",
      "static",
      "strictfp",
      "super",
      "switch",
      "synchronized",
      "this",
      "throw",
      "throws",
      "transient",
      "try",
      "void",
      "volatile",
      "while",
      // Reserved Keywords for Literals
      "false",
      "null",
      "true",
      // Reserved names for internal variables
      "value",
      "values",
      "input",
      "output",
      "tag",
      "other",
      "o",
      "size",
      "unknownBytes",
      "cachedSize",
      "bitfield0_",
      "unknownBytesFieldName",
    ),
  )

  private val collidingFieldSet = withCamelCaseNames(
    "class", // Object::getClass
    "missing_fields", // getMissingFields
    "unknown_bytes", // getUnknownFields
    "serialized_size", // getSerializedSize
    "cached_size", // getSerializedSize
    "descriptor", // getDescriptor
  )

  private def withCamelCaseNames(fieldNames: String*) =
    val set = new util.HashSet[String](fieldNames.length * 2)
    for (fieldName <- fieldNames) {
      set.add(fieldName)
      set.add(underscoresToCamelCaseImpl(fieldName, false))
    }
    set
