package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.SourceCodeInfo
import com.palantir.javapoet.CodeBlock

import java.util.Locale

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

/** Utilities for creating Javadoc comments on methods and fields. For the most part similar to
  * Protobuf-Java.
  *
  * @author
  *   Florian Enner
  * @author
  *   Piotr Sowiński
  */
object Javadoc:
  private val cachedFields = new java.util.HashMap[DescriptorProtos.FieldDescriptorProto, CodeBlock]

  def inherit: String =
    // Note: JavaDoc automatically adds the superclass doc,
    // so we don't need to manually call it out.
    //        return "{@inheritDoc}";
    ""

  def forMessage(info: RequestInfo.MessageInfo): CodeBlock = forType("type", info)

  def forMessageField(info: RequestInfo.FieldInfo): CodeBlock.Builder =
    // Fields get called a lot, so we cache the expensive parts
    var block = cachedFields.get(info.descriptor)
    if (block == null) {
      block = withComments(info.sourceLocation)
        .add("<code>$L</code>", getFieldDefinitionLine(info.descriptor)).build
      cachedFields.put(info.descriptor, block)
    }
    block.toBuilder

  def forOneOfField(info: RequestInfo.OneOfInfo): CodeBlock.Builder =
    CodeBlock.builder
      .add("<code>oneof $L { ... }</code>", info.descriptor.getName)

  def forEnum(info: RequestInfo.EnumInfo): CodeBlock = forType("enum", info)

  def forEnumValue(info: RequestInfo.EnumValueInfo): CodeBlock =
    withComments(info.sourceLocation)
      .add("<code>$L = $L;</code>", info.getName, info.getNumber)
      .build

  def withComments(location: SourceCodeInfo.Location): CodeBlock.Builder =
    // Protobuf-java seems to prefer leading comments and only use trailing as a fallback.
    // They also remove the first space as well as empty lines, but that'
    val builder = CodeBlock.builder
    val format = "<pre>\n$L</pre>\n\n"
    if (location.hasLeadingComments)
      builder.add(format, escapeCommentClose(location.getLeadingComments))
    else if (location.hasTrailingComments)
      builder.add(format, escapeCommentClose(location.getTrailingComments))
    builder

  private def forType(name: String, info: RequestInfo.TypeInfo) =
    withComments(info.sourceLocation)
      .add("Protobuf $L {@code $T}", name, info.typeName)
      .add(
        "\nDo not inherit from this class!\n" +
          "It's not <code>final</code> only to facilitate the Mutable nested subclass.",
      )
      .build

  private def getFieldDefinitionLine(descriptor: DescriptorProtos.FieldDescriptorProto) = {
    // optional int32 my_field = 2 [default = 1];
    val label = descriptor.getLabel.toString.substring("LABEL_".length).toLowerCase(Locale.US)
    var t = descriptor.getTypeName
    if (t.isEmpty) t = descriptor.getType.toString.substring("TYPE_".length).toLowerCase(Locale.US)
    val definition =
      String.format("%s %s %s = %d", label, t, descriptor.getName, descriptor.getNumber)
    var options = ""
    if (descriptor.hasDefaultValue) {
      val defaultValue = escapeCommentClose(descriptor.getDefaultValue)
      options = " [default = " + defaultValue + "]"
    } else if (descriptor.getOptions.hasPacked)
      options = " [packed = " + descriptor.getOptions.getPacked + "]"
    val line = definition + options + ";"
    if (!descriptor.hasExtendee) line
    else "extend {\n  " + line + "\n}"
  }

  private def escapeCommentClose(string: String) =
    if (string.contains("*/")) string.replaceAll("\\*/", "*\\\\/")
    else string
