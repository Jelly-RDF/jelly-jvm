package eu.neverblink.protoc.java.gen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.*

import java.util.Comparator

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
object FieldUtil:
  private val WIRETYPE_VARINT = 0
  private val WIRETYPE_FIXED64 = 1
  private val WIRETYPE_LENGTH_DELIMITED = 2
  private val WIRETYPE_START_GROUP = 3
  private val WIRETYPE_END_GROUP = 4
  private val WIRETYPE_FIXED32 = 5
  private val TAG_TYPE_BITS = 3
  private val TAG_TYPE_MASK = (1 << TAG_TYPE_BITS) - 1

  def makeTag(descriptor: DescriptorProtos.FieldDescriptorProto): Int =
    descriptor.getNumber << TAG_TYPE_BITS | getWireType(descriptor.getType)

  def makePackedTag(descriptor: DescriptorProtos.FieldDescriptorProto): Int =
    descriptor.getNumber << 3 | WIRETYPE_LENGTH_DELIMITED

  def makeGroupEndTag(startTag: Int): Int =
    val fieldId = startTag >>> TAG_TYPE_BITS
    fieldId << TAG_TYPE_BITS | WIRETYPE_END_GROUP

  /** Compute the number of bytes that would be needed to encode a varint. <pre>value</pre> is
    * treated as unsigned, so it won't be sign-extended if negative.
    */
  def computeRawVarint32Size(value: Int): Int =
    if ((value & (0xffffffff << 7)) == 0) return 1
    if ((value & (0xffffffff << 14)) == 0) return 2
    if ((value & (0xffffffff << 21)) == 0) return 3
    if ((value & (0xffffffff << 28)) == 0) return 4
    5

  /** OneOf bits and required bits should be close together so that it is more likely for them to be
    * checked in a single bit comparison. This sorter groups all required fields, followed by OneOf
    * groups, followed by everything else.
    */
  val GroupOneOfAndRequiredBits: Comparator[FieldDescriptorProto] =
    new Comparator[FieldDescriptorProto]:
      override def compare(o1: FieldDescriptorProto, o2: FieldDescriptorProto): Int =
        val n1 =
          if (o1.hasOneofIndex) o1.getOneofIndex
          else if (o1.getLabel eq Label.LABEL_REQUIRED) Integer.MAX_VALUE
          else -1
        val n2 =
          if (o2.hasOneofIndex) o2.getOneofIndex
          else if (o2.getLabel eq Label.LABEL_REQUIRED) Integer.MAX_VALUE
          else -1
        n2 - n1

  /** Sort fields according to their specified field number. This is used as the serialization order
    * by Google's protobuf bindings.
    */
  val AscendingNumberSorter: Comparator[FieldGenerator] =
    Comparator.comparingInt((field: FieldGenerator) => field.info.number)

  /** Sort the fields according to their layout in memory. <p> Summary:
    *   - Objects are 8 bytes aligned in memory (address A is K aligned if A % K == 0)
    *   - All fields are type aligned (long/double is 8 aligned, integer/float 4, short/char 2)
    *   - Fields are packed in the order of their size, except for references which are last
    *   - Classes fields are never mixed, so if B extends A, A's fields will be laid out first
    *   - Sub class fields start at a 4 byte alignment
    *   - If the first field of a class is long/double and the class starting point (after header,
    *     or after super) is not 8 aligned then a smaller field may be swapped to fill in the 4
    *     bytes gap. <p> For more info, see
    *     http://psy-lob-saw.blogspot.com/2013/05/know-thy-java-object-memory-layout.html
    */
  val MemoryLayoutSorter: Comparator[FieldDescriptorProto] = new Comparator[FieldDescriptorProto]:
    override def compare(objA: FieldDescriptorProto, objB: FieldDescriptorProto): Int =
      // The higher the number, the closer to the beginning
      val weightA = getSortingWeight(objA) + (if (objA.getLabel eq Label.LABEL_REPEATED) -50
                                              else 0)
      val weightB = getSortingWeight(objB) + (if (objB.getLabel eq Label.LABEL_REPEATED) -50
                                              else 0)
      // Higher field number -> lower ranking
      if weightA == weightB then objA.getNumber - objB.getNumber
      else weightB - weightA

  private def getSortingWeight(descriptor: FieldDescriptorProto): Int =
    // Start with largest width and get smaller. References come at the end.
    // For memory layout we only need to look at the base type, but it
    // can't hurt to sort by exact type to maybe keep the serialization code
    // in cache hot.
    // TODO: Piotr could not figure out what this was supposed to be doing, so here's a 1
    1

  private def getWireType(t: FieldDescriptorProto.Type): Int = t match
    case TYPE_UINT64 => WIRETYPE_VARINT
    case TYPE_INT64 => WIRETYPE_VARINT
    case TYPE_UINT32 => WIRETYPE_VARINT
    case TYPE_INT32 => WIRETYPE_VARINT
    case TYPE_BOOL => WIRETYPE_VARINT
    case TYPE_ENUM => WIRETYPE_VARINT
    case TYPE_SINT32 => WIRETYPE_VARINT
    case TYPE_SINT64 => WIRETYPE_VARINT
    case TYPE_SFIXED64 => WIRETYPE_FIXED64
    case TYPE_FIXED64 => WIRETYPE_FIXED64
    case TYPE_DOUBLE => WIRETYPE_FIXED64
    case TYPE_BYTES => WIRETYPE_LENGTH_DELIMITED
    case TYPE_STRING => WIRETYPE_LENGTH_DELIMITED
    case TYPE_MESSAGE => WIRETYPE_LENGTH_DELIMITED
    case TYPE_GROUP => WIRETYPE_START_GROUP
    case TYPE_SFIXED32 => WIRETYPE_FIXED32
    case TYPE_FIXED32 => WIRETYPE_FIXED32
    case TYPE_FLOAT => WIRETYPE_FIXED32

  /** Used for e.g. writeUInt64() methods
    *
    * @param t
    * @return
    */
  def getCapitalizedType(t: FieldDescriptorProto.Type): String = t match
    case TYPE_DOUBLE => "Double"
    case TYPE_FLOAT => "Float"
    case TYPE_INT64 => "Int64"
    case TYPE_UINT64 => "UInt64"
    case TYPE_INT32 => "Int32"
    case TYPE_FIXED64 => "Fixed64"
    case TYPE_FIXED32 => "Fixed32"
    case TYPE_BOOL => "Bool"
    case TYPE_STRING => "String"
    case TYPE_GROUP => "Group"
    case TYPE_MESSAGE => "Message"
    case TYPE_BYTES => "Bytes"
    case TYPE_UINT32 => "UInt32"
    case TYPE_ENUM => "Enum"
    case TYPE_SFIXED32 => "SFixed32"
    case TYPE_SFIXED64 => "SFixed64"
    case TYPE_SINT32 => "SInt32"
    case TYPE_SINT64 => "SInt64"

  def isFixedWidth(t: FieldDescriptorProto.Type): Boolean = getFixedWidth(t) > 0

  def getFixedWidth(t: FieldDescriptorProto.Type): Int = t match
    // 64 bit
    case TYPE_DOUBLE => 8
    case TYPE_SFIXED64 => 8
    case TYPE_FIXED64 => 8
    // 32 bit
    case TYPE_FLOAT => 4
    case TYPE_SFIXED32 => 4
    case TYPE_FIXED32 => 4
    // 8 bit
    case TYPE_BOOL => 1
    // varint
    case _ => -1

  def isPrimitive(t: FieldDescriptorProto.Type): Boolean = t match
    case TYPE_DOUBLE => true
    case TYPE_FLOAT => true
    case TYPE_INT64 => true
    case TYPE_UINT64 => true
    case TYPE_INT32 => true
    case TYPE_FIXED64 => true
    case TYPE_FIXED32 => true
    case TYPE_BOOL => true
    case TYPE_UINT32 => true
    case TYPE_SFIXED32 => true
    case TYPE_SFIXED64 => true
    case TYPE_SINT32 => true
    case TYPE_SINT64 => true
    case TYPE_ENUM => false
    case TYPE_STRING => false
    case TYPE_GROUP => false
    case TYPE_MESSAGE => false
    case TYPE_BYTES => false

  def getEmptyDefaultValue(t: FieldDescriptorProto.Type): String = t match
    case TYPE_DOUBLE => "0D"
    case TYPE_FLOAT => "0F"
    case TYPE_SFIXED64 => "0L"
    case TYPE_FIXED64 => "0L"
    case TYPE_SINT64 => "0L"
    case TYPE_INT64 => "0L"
    case TYPE_UINT64 => "0L"
    case TYPE_SFIXED32 => "0"
    case TYPE_FIXED32 => "0"
    case TYPE_SINT32 => "0"
    case TYPE_INT32 => "0"
    case TYPE_UINT32 => "0"
    case TYPE_BOOL => "false"
    case TYPE_STRING => "\"\""
    case TYPE_ENUM => "UNSPECIFIED"
    case TYPE_GROUP => "null"
    case TYPE_MESSAGE => "null"
    case TYPE_BYTES => "???" // huh?

  /** Hash code for JSON field name lookup. Any changes need to be synchronized between
    * FieldUtil::hash32 and ProtoUtil::hash32.
    */
  def hash32(value: String): Int = value.hashCode
