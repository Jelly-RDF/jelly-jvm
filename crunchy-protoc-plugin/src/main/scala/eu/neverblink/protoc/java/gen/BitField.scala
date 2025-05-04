package eu.neverblink.protoc.java.gen

import com.palantir.javapoet.{FieldSpec, MethodSpec, TypeSpec}
import eu.neverblink.protoc.java.gen.RequestInfo.FieldInfo

import java.util
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
 * Utilities for creating protobuf-like bit-sets to keep has state
 *
 * @author Florian Enner
 * @author Piotr Sowiński
 */
object BitField:
  private val BITS_PER_FIELD = 32

  def getNumberOfFields(fieldCount: Int): Int =
    (fieldCount + (BITS_PER_FIELD - 1)) / BITS_PER_FIELD

  def generateMemberFields(t: TypeSpec.Builder, numBitFields: Int): Unit =
    // first bitfield is in the parent class
    for (i <- 1 until numBitFields) {
      t.addField(FieldSpec.builder(classOf[Int], BitField.fieldName(i), Modifier.PRIVATE).build)
    }

  def generateClearCode(clear: MethodSpec.Builder, numBitFields: Int): Unit =
    for (i <- 0 until numBitFields) {
      clear.addStatement("$L = 0", BitField.fieldName(i))
    }

  def getEqualsStatement(fieldIndex: Int): String =
    String.format("bitField%d_ == other.bitField%d_", fieldIndex, fieldIndex)

  def isCopyFromNeeded(fieldIndex: Int): String =
    String.format("(bitField%d_ | other.bitField%d_) != 0", fieldIndex, fieldIndex)

  def generateCopyFromCode(copyFrom: MethodSpec.Builder, fieldIndex: Int): Unit =
    copyFrom.addStatement("$1L = other.$1L", BitField.fieldName(fieldIndex))

  def hasAnyBit(fields: util.List[FieldInfo]): String =
    hasAnyBit(generateBitset(fields))

  def isMissingAnyBit(fields: util.List[FieldInfo]): String =
    isMissingAnyBit(generateBitset(fields))

  def isBitInField(bitIndex: Int, fieldIndex: Int): Boolean =
    getFieldIndex(bitIndex) == fieldIndex

  def hasBit(hasBitIndex: Int): String =
    String.format("(bitField%d_ & 0x%08x) != 0", getFieldIndex(hasBitIndex), 1 << getBitIndex(hasBitIndex))

  def setBit(hasBitIndex: Int): String =
    String.format("bitField%d_ |= 0x%08x", getFieldIndex(hasBitIndex), 1 << getBitIndex(hasBitIndex))

  def clearBit(hasBitIndex: Int): String =
    val field = getFieldIndex(hasBitIndex)
    String.format("bitField%d_ &= ~0x%08x", field, 1 << getBitIndex(hasBitIndex))

  def hasNoBits(numBitFields: Int): String =
    var output = "((" + fieldName(0)
    for (i <- 1 until numBitFields) {
      output += " | " + fieldName(i)
    }
    output + ") == 0)"

  private def isMissingAnyBit(bitset: Array[Int]) =
    val builder = new StringBuilder()
    var usedFields = 0
    for (i <- bitset.indices) {
      if bitset(i) != 0 then
        builder.append(if ( {
          usedFields += 1; usedFields - 1
        } == 0) "("
        else " || ")
        builder.append(String.format("((bitField%d_ & 0x%08x) != 0x%08x)", i, bitset(i), bitset(i)))
    }
    builder.append(if (usedFields > 0) ")"
    else "true")
    builder.toString

  private def hasAnyBit(bitset: Array[Int]) =
    val builder = new StringBuilder()
    var usedFields = 0
    for (i <- bitset.indices) {
      if bitset(i) != 0 then
        builder.append(if ( {
          usedFields += 1; usedFields - 1
        } == 0) "(("
        else " | ")
        builder.append(String.format("(bitField%d_ & 0x%08x)", i, bitset(i)))
    }
    builder.append(if (usedFields > 0) ") != 0)" else "true")
    builder.toString

  private def generateBitset(fields: util.List[FieldInfo]) =
    val maxIndex = fields.stream
      .mapToInt(_.bitIndex).max.orElse(0)
    val bits = new Array[Int](getNumberOfFields(maxIndex) + 1)
    for (field <- fields.asScala) {
      val fieldIndex = getFieldIndex(field.bitIndex)
      bits(fieldIndex) |= 1 << getBitIndex(field.bitIndex)
    }
    bits

  private def fieldName(intIndex: Int) = String.format("bitField%d_", intIndex)
  private def getFieldIndex(fieldIndex: Int) = fieldIndex / BITS_PER_FIELD
  private def getBitIndex(fieldIndex: Int) = fieldIndex % BITS_PER_FIELD

