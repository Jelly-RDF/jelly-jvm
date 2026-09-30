package eu.neverblink.protoc.java.runtime

import com.google.protobuf.{CodedInputStream, CodedOutputStream}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayOutputStream

class RepeatedIntSpec extends AnyWordSpec, Matchers:

  private def repeated(values: Seq[Int]): RepeatedInt =
    val r = RepeatedInt.newEmptyInstance()
    values.foreach(r.add)
    r

  private def varintSize(values: Seq[Int]): Int =
    values.map(CodedOutputStream.computeUInt32SizeNoTag).sum

  /** The values that writePackedUInt32 writes, read back. */
  private def writtenBack(r: RepeatedInt): Seq[Int] =
    val out = ByteArrayOutputStream()
    val coded = CodedOutputStream.newInstance(out)
    ProtobufUtil.writePackedUInt32(coded, r)
    coded.flush()
    val in = CodedInputStream.newInstance(out.toByteArray)
    val limit = in.pushLimit(in.readRawVarint32())
    val values = Seq.newBuilder[Int]
    while !in.isAtEnd do values += in.readRawVarint32()
    in.popLimit(limit)
    values.result()

  "RepeatedInt" should {
    "measure its values as uint32 varints" in {
      val values = Seq(0, 1, 127, 128, 16_383, 16_384, Int.MaxValue, -1)
      val r = repeated(values)
      r.computeUInt32SizeNoTag() shouldBe varintSize(values)
      r.uint32SizeNoTag() shouldBe varintSize(values)
    }

    "measure again when values were added since" in {
      val r = repeated(Seq(1, 2, 3))
      r.computeUInt32SizeNoTag() shouldBe 3
      r.add(1_000_000)
      r.uint32SizeNoTag() shouldBe 3 + 3
      writtenBack(r) shouldBe Seq(1, 2, 3, 1_000_000)
    }

    "not keep the size of values that were cleared" in {
      val r = repeated(Seq(1, 2, 3))
      r.computeUInt32SizeNoTag() shouldBe 3
      r.clear()
      // As many values as before, but larger
      Seq(300, 300, 300).foreach(r.add)
      r.uint32SizeNoTag() shouldBe 6
      writtenBack(r) shouldBe Seq(300, 300, 300)
    }

    "write packed values with the size that was measured" in {
      val values = (0 until 1000).map(i => i * 997)
      val r = repeated(values)
      r.computeUInt32SizeNoTag()
      writtenBack(r) shouldBe values
    }
  }
