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

  /** What protobuf writes for a string, without the length. */
  private def protobufBytes(s: String): Array[Byte] =
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)
    coded.writeStringNoTag(s)
    coded.flush()
    CodedInputStream.newInstance(out.toByteArray).readByteArray()

  private def repeated(values: Seq[String]): RepeatedString =
    val r = RepeatedString.newEmptyInstance()
    values.foreach(r.add)
    r

  "RepeatedString" should {
    "give the bytes that protobuf writes for each value" in {
      val r = repeated(strings)
      for (s, i) <- strings.zipWithIndex do
        withClue(s"value $i: ") {
          val expected = protobufBytes(s)
          // Before and after the size has been computed
          r.utf8(i) shouldBe expected
          r.computeStringSizeNoTag()
          r.utf8(i) shouldBe expected
        }
    }

    "compute the same size as protobuf" in {
      val r = repeated(strings)
      r.computeStringSizeNoTag() shouldBe strings.map(CodedOutputStream.computeStringSizeNoTag).sum
    }

    "not give the bytes of a value that is no longer there" in {
      val r = repeated(Seq("first", "second"))
      r.computeStringSizeNoTag()
      r.clear()
      r.add("other")
      r.utf8(0) shouldBe "other".getBytes("UTF-8")
      // Values added after the size was computed, also beyond what it covered
      r.computeStringSizeNoTag()
      for i <- 1 to 20 do r.add(s"v$i")
      for i <- 1 to 20 do r.utf8(i) shouldBe s"v$i".getBytes("UTF-8")
      val copy = RepeatedString.newEmptyInstance()
      copy.addAll(r)
      copy.utf8(0) shouldBe "other".getBytes("UTF-8")
      an[IndexOutOfBoundsException] should be thrownBy r.utf8(21)
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
