package eu.neverblink.jelly.core.helpers

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Descriptors.{Descriptor, FieldDescriptor}
import com.google.protobuf.Descriptors.FieldDescriptor.Type

import java.io.ByteArrayOutputStream
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters.*
import scala.util.Random

/** Random messages in the protobuf wire format, built from a message descriptor.
  *
  * Every field may be skipped, a repeated field gets 0–3 values (packed or not, if it can be
  * packed), and the fields are written in a random order. Values and strings are kept small, so
  * that many messages are only a few bytes long. The content is not valid Jelly: this is for code
  * that only looks at the wire format, like the detection of delimited streams.
  */
object WireFuzzer:
  private val maxDepth = 4

  def message(descriptor: Descriptor, rnd: Random): Array[Byte] = message(descriptor, rnd, 0)

  private def message(descriptor: Descriptor, rnd: Random, depth: Int): Array[Byte] =
    val fields = ArrayBuffer[Array[Byte]]()
    for field <- descriptor.getFields.asScala do
      val nested = field.getType == Type.MESSAGE || field.getType == Type.GROUP
      if !(nested && depth >= maxDepth) && field.getType != Type.GROUP then
        if field.isRepeated then
          val count = rnd.nextInt(4)
          if count > 0 && field.isPackable && rnd.nextBoolean() then
            fields += packed(field, count, rnd)
          else for _ <- 0 until count do fields += single(field, rnd, depth)
        else if rnd.nextInt(3) == 0 then fields += single(field, rnd, depth)
    concat(rnd.shuffle(fields))

  private def single(field: FieldDescriptor, rnd: Random, depth: Int): Array[Byte] =
    write { out =>
      field.getType match
        case Type.MESSAGE =>
          val bytes = message(field.getMessageType, rnd, depth + 1)
          out.writeTag(field.getNumber, 2)
          out.writeUInt32NoTag(bytes.length)
          out.writeRawBytes(bytes)
        case Type.STRING | Type.BYTES =>
          val bytes = Array.fill(smallLength(rnd))(('a' + rnd.nextInt(26)).toByte)
          out.writeTag(field.getNumber, 2)
          out.writeUInt32NoTag(bytes.length)
          out.writeRawBytes(bytes)
        case _ =>
          out.writeTag(field.getNumber, wireType(field))
          value(field, out, rnd)
    }

  private def packed(field: FieldDescriptor, count: Int, rnd: Random): Array[Byte] =
    val values = write(out => for _ <- 0 until count do value(field, out, rnd))
    write { out =>
      out.writeTag(field.getNumber, 2)
      out.writeUInt32NoTag(values.length)
      out.writeRawBytes(values)
    }

  private def value(field: FieldDescriptor, out: CodedOutputStream, rnd: Random): Unit =
    wireType(field) match
      case 1 => out.writeFixed64NoTag(rnd.nextLong())
      case 5 => out.writeFixed32NoTag(rnd.nextInt())
      case _ =>
        // Mostly small values, sometimes up to the full 64 bits
        val v = rnd.nextInt(4) match
          case 0 => rnd.nextLong()
          case 1 => rnd.nextInt(1 << 20).toLong
          case _ => rnd.nextInt(130).toLong
        out.writeUInt64NoTag(v)

  private def wireType(field: FieldDescriptor): Int = field.getType match
    case Type.DOUBLE | Type.FIXED64 | Type.SFIXED64 => 1
    case Type.FLOAT | Type.FIXED32 | Type.SFIXED32 => 5
    case Type.MESSAGE | Type.STRING | Type.BYTES => 2
    case _ => 0

  private def smallLength(rnd: Random): Int =
    if rnd.nextInt(8) == 0 then rnd.nextInt(200) else rnd.nextInt(13)

  private def write(f: CodedOutputStream => Unit): Array[Byte] =
    val bytes = ByteArrayOutputStream()
    val out = CodedOutputStream.newInstance(bytes)
    f(out)
    out.flush()
    bytes.toByteArray

  private def concat(parts: Iterable[Array[Byte]]): Array[Byte] =
    val bytes = ByteArrayOutputStream()
    parts.foreach(bytes.write)
    bytes.toByteArray
