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

    "write values it never measured" in {
      val values = Seq(0, 127, 128, Int.MaxValue, -1)
      writtenBack(repeated(values)) shouldBe values
    }

    "write the values again after they were cleared and refilled" in {
      val r = repeated((0 until 100).map(_ * 100_000))
      r.computeUInt32SizeNoTag()
      r.clear()
      Seq(5, 6).foreach(r.add)
      r.computeUInt32SizeNoTag() shouldBe 2
      writtenBack(r) shouldBe Seq(5, 6)
    }

    "hold the values of a wrapped array and write them" in {
      val r = repeated(Seq(1, 2, 3))
      r.computeUInt32SizeNoTag()
      r.wrap(Array(7, 300, 9, 99), 3)
      r.size shouldBe 3
      r.get(1) shouldBe 300
      r.uint32SizeNoTag() shouldBe 4
      writtenBack(r) shouldBe Seq(7, 300, 9)
      an[IndexOutOfBoundsException] should be thrownBy r.wrap(Array(1), 2)
    }

    "round-trip random values (fuzzing)" in {
      val random = scala.util.Random(42)
      // Values below 128 (one byte each, the copy path), of any varint length, or a mix
      val kinds: Seq[() => Int] = Seq(
        () => random.nextInt(128),
        () => random.nextInt(),
        () => if random.nextInt(10) == 0 then random.nextInt() else random.nextInt(128),
        () => 1 << random.nextInt(32),
      )
      for round <- 1 to 2000 do
        val next = kinds(round % kinds.size)
        val values = Seq.fill(random.nextInt(70))(next())
        withClue(s"round $round, values $values: ") {
          val r = repeated(values)
          if round % 2 == 0 then r.computeUInt32SizeNoTag() shouldBe varintSize(values)
          r.uint32SizeNoTag() shouldBe varintSize(values)
          writtenBack(r) shouldBe values
        }
    }

    "write packed values with the size that was measured" in {
      val values = (0 until 1000).map(i => i * 997)
      val r = repeated(values)
      r.computeUInt32SizeNoTag()
      writtenBack(r) shouldBe values
    }
  }
