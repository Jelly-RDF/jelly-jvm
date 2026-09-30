package eu.neverblink.jelly.core

import com.google.protobuf.CodedOutputStream
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.protoc.java.runtime.ProtoMessage
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayOutputStream

/** Singular string fields write the UTF-8 bytes they were measured from (see FieldGenerator). */
class SingularStringUtf8Spec extends AnyWordSpec, Matchers:

  private val strings = Seq(
    "",
    "a",
    "https://example.org/" + "x" * 100,
    "y" * 5000,
    "é中😀",
    // Unpaired surrogates, which protobuf writes as '?'
    "a\uD800b",
  )

  /** What protobuf-java writes for an RdfNameEntry with this value. */
  private def expected(id: Int, value: String): Array[Byte] =
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)
    if id != 0 then coded.writeUInt32(1, id)
    if value.nonEmpty then coded.writeString(2, value)
    coded.flush()
    out.toByteArray

  "a singular string field" should {
    "be written as protobuf writes it" in {
      for s <- strings do
        withClue(s"'${s.take(20)}': ") {
          ProtoMessage.toByteArray(RdfNameEntry.newInstance().setId(7).setValue(s)) shouldBe
            expected(7, s)
        }
    }

    "be written with its current value, even when set after the size was computed" in {
      val entry = RdfNameEntry.newInstance().setId(1).setValue("aaaa")
      entry.getSerializedSize
      // Same length, so the cached size is still right – but the bytes are not
      entry.setValue("bbbb")
      ProtoMessage.toByteArray(entry) shouldBe expected(1, "bbbb")
      RdfNameEntry.parseFrom(ProtoMessage.toByteArray(entry)).getValue shouldBe "bbbb"
    }

    "be written again from the same bytes" in {
      val literal =
        RdfLiteral.newInstance().setLex("https://example.org/" + "z" * 60).setDatatype(3)
      val first = ProtoMessage.toByteArray(literal)
      ProtoMessage.toByteArray(literal) shouldBe first
      RdfLiteral.parseFrom(first).getLex shouldBe literal.getLex
    }
  }
