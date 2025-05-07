package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos
import com.palantir.javapoet.{CodeBlock, FieldSpec, MethodSpec, TypeSpec}

import java.util
import java.util.Base64
import javax.lang.model.element.Modifier
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

/**
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object DescriptorGenerator {
  private def getDescriptorBytesFieldName = "descriptorData"

  private def getFileDescriptorFieldName = "descriptor"

  def getDescriptorFieldName(info: RequestInfo.MessageInfo): String =
    // uniquely identifiable descriptor name. Similar to protobuf-java
    // but without the "internal_static_" prefix.
    info.fullName.replaceAll("\\.", "_") + "_descriptor"

  /**
   * The Protobuf-Java descriptor does some symbol stripping (e.g. jsonName only appears if it was specified),
   * so serializing the raw descriptor does not produce binary compatibility. I don't know whether it's worth
   * implementing it, so for now we leave it empty. See
   * https://github.com/protocolbuffers/protobuf/blob/209accaf6fb91aa26e6086e73626e1884ddfb737/src/google/protobuf/compiler/retention.cc#L105-L116
   * Note that this would also need to be stripped from Message descriptors to work with offsets.
   */
  private def stripSerializedDescriptor(descriptor: DescriptorProtos.FileDescriptorProto) = descriptor
}

class DescriptorGenerator(val info: RequestInfo.FileInfo):
  final val m = new util.HashMap[String, AnyRef]
  m.put("abstractMessage", RuntimeClasses.AbstractMessage)
  m.put("protoUtil", RuntimeClasses.ProtoUtil)
  private val fileDescriptorBytes: Array[Byte] = DescriptorGenerator.stripSerializedDescriptor(info.descriptor).toByteArray

  def generate(t: TypeSpec.Builder): Unit =
    // bytes shared by everything
    t.addField(FieldSpec
      .builder(Helpers.TYPE_BYTE_ARRAY, DescriptorGenerator.getDescriptorBytesFieldName)
      .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
      .initializer(generateEmbeddedByteBlock(fileDescriptorBytes))
      .build
    )
    // field for the main file descriptor
    val initBlock = CodeBlock.builder
    initBlock.add(
      "$T.buildFrom($T.parseFrom($N), new $T[] { ",
      RuntimeClasses.FileDescriptor,
      RuntimeClasses.FileDescriptorProto,
      DescriptorGenerator.getDescriptorBytesFieldName,
      RuntimeClasses.FileDescriptor,
    )
    // any file dependencies
    if (info.descriptor.getDependencyCount > 0) {
      for ((fileName, i) <- info.descriptor.getDependencyList.asScala.zipWithIndex) {
        if i > 0 then initBlock.add(", ")
        initBlock.add("$T.getDescriptor()", info.parentRequest.getInfoForFile(fileName).outerClassName)
      }
    }
    initBlock.add(" })")
    val fileDescriptor = FieldSpec
      .builder(RuntimeClasses.FileDescriptor, DescriptorGenerator.getFileDescriptorFieldName)
      .addModifiers(Modifier.STATIC, Modifier.FINAL)
      .build
    t.addField(fileDescriptor)

    // Add a static method
    t.addMethod(MethodSpec.methodBuilder("getDescriptor")
      .addJavadoc("@return this proto file's descriptor.")
      .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
      .returns(RuntimeClasses.FileDescriptor)
      .addStatement("return $N", DescriptorGenerator.getFileDescriptorFieldName)
      .build
    )
    // Descriptor field for each nested type
    val staticBlock = CodeBlock.builder
    val getBase = CodeBlock.of("$N.getMessageTypes()", DescriptorGenerator.getFileDescriptorFieldName)
    for (message, ix) <- info.messageTypes.asScala.zipWithIndex do
      addMessageDescriptor(
        t,
        staticBlock,
        getBase,
        message,
        ix
      )
    
    t.addStaticBlock(CodeBlock.builder
      .beginControlFlow("try")
      .addStatement("descriptor = $L", initBlock.build)
      .add(staticBlock.build)
      .nextControlFlow("catch ($T e)", RuntimeClasses.Exception)
      .addStatement("throw new $T(e)", RuntimeClasses.RuntimeException)
      .endControlFlow
      .build
    )

  private def addMessageDescriptor(
    t: TypeSpec.Builder,
    staticBlock: CodeBlock.Builder,
    getBase: CodeBlock,
    message: RequestInfo.MessageInfo,
    index: Int,
  ): Unit =
    val msgDesc = message.descriptor
    val descriptorBytes = message.descriptor.toByteArray
    t.addField(FieldSpec
      .builder(RuntimeClasses.MessageDescriptor, DescriptorGenerator.getDescriptorFieldName(message))
      .addModifiers(Modifier.STATIC, Modifier.FINAL)
      .build
    )
    staticBlock.addStatement(
      "$N = " + getBase + ".get($L)",
      DescriptorGenerator.getDescriptorFieldName(message),
      index
    )
    // Recursively add nested messages
    val nestedGetBase = CodeBlock.of("$N.getNestedTypes()", DescriptorGenerator.getDescriptorFieldName(message))
    for ((nestedType, j) <- message.nestedTypes.asScala.zipWithIndex) {
      addMessageDescriptor(t, staticBlock, nestedGetBase, nestedType, j)
    }
    

  private def generateEmbeddedByteBlock(descriptor: Array[Byte]) =
    // Inspired by Protoc's SharedCodeGenerator::GenerateDescriptors:
    //
    // Embed the descriptor.  We simply serialize the entire FileDescriptorProto
    // and embed it as a string literal, which is parsed and built into real
    // descriptors at initialization time.  We unfortunately have to put it in
    // a string literal, not a byte array, because apparently using a literal
    // byte array causes the Java compiler to generate *instructions* to
    // initialize each and every byte of the array, e.g. as if you typed:
    //   b[0] = 123; b[1] = 456; b[2] = 789;
    // This makes huge bytecode files and can easily hit the compiler's internal
    // code size limits (error "code to large").  String literals are apparently
    // embedded raw, which is what we want.
    val charsPerLine = 80 // should be a multiple of 4

    // Construct bytes from individual base64 String sections
    val initBlock = CodeBlock.builder
    initBlock
      .add("$T.getDecoder().decode(", RuntimeClasses.Base64)
      .add("$>")
    val block = Base64.getEncoder.encodeToString(descriptor)
    //var line = block.substring(0, Math.min(charsPerLine, block.length))
    var blockIx = 0
    while (blockIx < block.length) {
      val line = block.substring(blockIx, Math.min(blockIx + charsPerLine, block.length))
      if blockIx > 0 then initBlock.add(" + \n$S", line)
      else initBlock.add("\n$S", line)
      blockIx += charsPerLine
    }
    initBlock.add(")$<")
    initBlock.build

