package eu.neverblink.protoc.java.runtime

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import eu.neverblink.jelly.core.proto.v1.RdfLookupEntryPacked
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayOutputStream

class RepeatedStringSpec extends AnyWordSpec, Matchers:

  private val strings = Seq(
    "",
    "a",
    "https://example.org/ns#term",
    // Longer than 42 characters: protobuf measures these before writing them
    "https://example.org/" + "x" * 100,
    // Longer than the 8 KB output buffer / 3: protobuf encodes these into an array of their own
    "y" * 5000,
    "é",
    "中文",
    "😀",
    // Unpaired surrogates, which protobuf writes as '?'
    "a\uD800b",
    "\uDC00",
  )

  private val tag = 18 // field 2, length-delimited

  /** What protobuf writes for the strings as a repeated field. */
  private def protobufBytes(values: Seq[String]): Array[Byte] =
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)
    values.foreach(coded.writeString(2, _))
    coded.flush()
    out.toByteArray

  private def written(r: RepeatedString): Array[Byte] =
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)
    r.writeWithTag(coded, tag)
    coded.flush()
    out.toByteArray

  /** The strings of a repeated field, read back with protobuf's own reader. */
  private def readBack(bytes: Array[Byte]): Seq[String] =
    val in = CodedInputStream.newInstance(bytes)
    val values = Seq.newBuilder[String]
    while !in.isAtEnd do
      in.readTag() shouldBe tag
      values += in.readString()
    values.result()

  private def repeated(values: Seq[String]): RepeatedString =
    val r = RepeatedString.newEmptyInstance()
    values.foreach(r.add)
    r

  "RepeatedString" should {
    "write the bytes that protobuf writes, measured or not" in {
      for s <- strings do
        withClue(s"value ${s.take(20)}: ") {
          val r = repeated(Seq(s))
          written(r) shouldBe protobufBytes(Seq(s))
          r.computeStringSizeWithTag(tag)
          written(r) shouldBe protobufBytes(Seq(s))
        }
      val r = repeated(strings)
      r.computeStringSizeWithTag(tag)
      written(r) shouldBe protobufBytes(strings)
    }

    "compute the same size as protobuf" in {
      val r = repeated(strings)
      r.computeStringSizeNoTag() shouldBe strings.map(CodedOutputStream.computeStringSizeNoTag).sum
      r.computeStringSizeWithTag(tag) shouldBe strings.map(CodedOutputStream.computeStringSize(2, _)).sum
    }

    "not write values that are no longer there, and write values added since it was measured" in {
      val r = repeated(Seq("first", "second"))
      r.computeStringSizeWithTag(tag)
      r.clear()
      r.add("other")
      written(r) shouldBe protobufBytes(Seq("other"))
      r.computeStringSizeWithTag(tag)
      val added = (1 to 20).map(i => s"v$i")
      added.foreach(r.add)
      written(r) shouldBe protobufBytes("other" +: added)
      val copy = RepeatedString.newEmptyInstance()
      copy.computeStringSizeWithTag(tag)
      copy.addAll(r)
      written(copy) shouldBe protobufBytes("other" +: added)
      // Measured with another tag: written with the one asked for
      copy.computeStringSizeWithTag(10)
      written(copy) shouldBe protobufBytes("other" +: added)
    }

    "round-trip random strings (fuzzing)" in {
      val random = scala.util.Random(42)
      // Code points of 1, 2, 3 and 4 bytes in UTF-8 (the last as surrogate pairs in Java)
      val ranges = Seq((0x00, 0x7f), (0x80, 0x7ff), (0x800, 0xd7ff), (0xe000, 0xffff), (0x10000, 0x10ffff))
      def randomString(ascii: Boolean): String =
        // Mostly short, sometimes long enough for a 2- or 3-byte length
        val length = if random.nextInt(10) == 0 then random.nextInt(5000) else random.nextInt(40)
        val sb = java.lang.StringBuilder()
        for _ <- 0 until length do
          val (from, to) = if ascii then ranges.head else ranges(random.nextInt(ranges.size))
          sb.appendCodePoint(from + random.nextInt(to - from + 1))
        sb.toString
      for round <- 1 to 1000 do
        val ascii = round % 3 == 0
        val values = Seq.fill(random.nextInt(30))(randomString(ascii))
        withClue(s"round $round, ${values.size} values: ") {
          val r = repeated(values)
          if round % 2 == 0 then
            r.computeStringSizeWithTag(tag) shouldBe values.map(CodedOutputStream.computeStringSize(2, _)).sum
          readBack(written(r)) shouldBe values
        }
    }

    "be written by generated messages as protobuf would write them" in {
      val entry = RdfLookupEntryPacked.newInstance().setId(3)
      strings.foreach(entry.getValues.add)
      val bytes = ProtoMessage.toByteArray(entry)
      // The same bytes, field by field, as protobuf's own string writer makes
      val out = ByteArrayOutputStream()
      val coded = CodedOutputStream.newInstance(out)
      coded.writeUInt32(1, 3)
      strings.foreach(coded.writeString(2, _))
      coded.flush()
      bytes shouldBe out.toByteArray
    }
  }
