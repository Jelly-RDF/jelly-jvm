package eu.neverblink.protoc.java.runtime

import com.google.protobuf.CodedOutputStream
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
