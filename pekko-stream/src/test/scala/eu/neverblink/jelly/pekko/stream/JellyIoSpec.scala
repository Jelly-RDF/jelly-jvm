package eu.neverblink.jelly.pekko.stream

import com.google.protobuf.CodedOutputStream
import eu.neverblink.jelly.core.ProtoTestCases.*
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfNameEntry, RdfStreamFrame, RdfStreamRow}
import eu.neverblink.jelly.core.{JellyOptions, ProtoTestCases}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.stream.scaladsl.Framing.FramingException
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.util.Random

class JellyIoSpec extends AnyWordSpec, Matchers, ScalaFutures:
  given ActorSystem = ActorSystem()

  val r = Random(42)

  val cases = Seq(
    ("triples, frame size 1", Triples1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.TRIPLES),
      1,
    )),
    ("triples, frame size 20", Triples1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.TRIPLES),
      20,
    )),
    ("quads, frame size 6", Quads1.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.QUADS),
      6,
    )),
    ("quads (2), frame size 2", Quads2RepeatDefault.encodedFull(
      JellyOptions.SMALL_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.QUADS),
      2,
    )),
    ("graphs, frame size 3", Graphs1.encodedFull(
      JellyOptions.BIG_GENERALIZED.clone().setPhysicalType(PhysicalStreamType.GRAPHS),
      3,
    ))
  )

  "toBytes and fromBytes" should {
    for (name, testCase) <- cases do
      s"work for $name" in {
        val decoded = Source.fromIterator(() => testCase.iterator)
          .via(JellyIo.toBytes)
          .via(JellyIo.fromBytes)
          .runWith(Sink.seq)
          .futureValue

        decoded shouldEqual testCase
      }
  }

  "toBytesDelimited" should {
    for (name, testCase) <- cases do
      s"work for $name" in {
        val decoded = Source.fromIterator(() => testCase.iterator)
          .via(JellyIo.toBytesDelimited)
          .mapConcat[RdfStreamFrame](b => {
            val is = ByteArrayInputStream(b)
            Seq(RdfStreamFrame.parseDelimitedFrom(is))
          })
          .runWith(Sink.seq)
          .futureValue

        decoded shouldEqual testCase
      }
  }

  "toIoStream and fromIoStream" should {
    for (name, testCase) <- cases do
      s"work for $name" in {
        val os = ByteArrayOutputStream()
        Source.fromIterator(() => testCase.iterator)
          .runWith(JellyIo.toIoStream(os))
          .futureValue

        val bytes = os.toByteArray
        bytes.length should be > 0

        val is = ByteArrayInputStream(bytes)
        val decoded = JellyIo.fromIoStream(is)
          .runWith(Sink.seq)
          .futureValue

        decoded shouldEqual testCase
      }
  }

  // (name, number of frames, frame size +-50%, input chunk size)
  private val framingTestCases = Seq(
    ("one frame in one ByteString", 1, 100, 1000),
    ("very small frames in one ByteString", 10, 5, 1000),
    ("0 or 1-length frames in one ByteString", 25, 0, 1000),
    ("one frame across 1-byte ByteStrings", 1, 200, 1),
    ("many frames across 10-byte ByteStrings", 50, 127, 100),
  )

  "protobufFraming" should {
    for (name, numFrames, frameSize, inputChunkSize) <- framingTestCases do
      s"work for $name" in {
        val sizes = collection.mutable.ListBuffer[Int]()
        val byteStrings = Iterator
          .continually(r.nextInt(frameSize + 2) + frameSize - (frameSize / 2))
          .take(numFrames)
          .map(size => {
            sizes += size
            val prefixLen = CodedOutputStream.computeInt32SizeNoTag(size)
            val data = new Array[Byte](size + prefixLen)
            val output = CodedOutputStream.newInstance(data)
            output.writeInt32NoTag(size)
            // And the rest we fill with a steady pattern
            for (i <- prefixLen until data.length) {
              data(i) = ((i - prefixLen) % 256).toByte
            }
            ByteString(data)
          })
          .reduce((a, b) => a ++ b)
          .grouped(inputChunkSize)
          .toSeq

        val result = Source(byteStrings)
          .via(JellyIo.protobufFraming(Int.MaxValue))
          .runWith(Sink.seq)
          .futureValue

        result.size shouldEqual numFrames
        for (byteString, expectedSize) <- result.zip(sizes) do {
          byteString.size shouldEqual expectedSize
          for (i <- byteString.indices) {
            byteString(i) shouldEqual ((i % 256).toByte)
          }
        }
      }

    "restrict maximum message size" in {
      val bos = ByteArrayOutputStream()
      val cos = CodedOutputStream.newInstance(bos)
      cos.writeInt32NoTag(1000) // size
      cos.flush()
      val input = ByteString(bos.toByteArray) ++ ByteString(Array.fill[Byte](1000)(1))

      val resultOk = Source.single(input)
        .via(JellyIo.protobufFraming(1000))
        .runWith(Sink.seq)
        .futureValue

      resultOk should have size 1
      resultOk.head.size shouldEqual 1000

      val resultFail = Source.single(input)
        .via(JellyIo.protobufFraming(999)) // too small
        .runWith(Sink.seq)

      val ex = resultFail.failed.futureValue
      ex shouldBe a[FramingException]
      ex.getMessage should include ("Maximum allowed Protobuf message size is 999 but decoded " +
        "delimiter reported size 1000")
    }

    "reject negative message size" in {
      val bytes = Array[Byte](
        143.toByte, // continuation bit set, 4 last bits set to 1
        0xff.toByte, 0xff.toByte, 0xff.toByte, // 3x continuation bit set, 7 bits set to 1
        127 // 7 bits set to 1, continuation bit not set
        // In total, this gives us 4 * 7 + 4 = 32 bits, which is -1 when interpreted as an Int
      )
      val input = ByteString(bytes)

      val resultFail = Source.single(input)
        .via(JellyIo.protobufFraming(1000)) // any size should do
        .runWith(Sink.seq)

      val ex = resultFail.failed.futureValue
      ex shouldBe a[FramingException]
      ex.getMessage should include ("Decoded Protobuf delimiter reported negative size -1")
    }

    "accept delimiter length of up to 5 bytes" in {
      val bos = ByteArrayOutputStream()
      val cos = CodedOutputStream.newInstance(bos)
      cos.writeInt32NoTag(Int.MaxValue) // size
      cos.flush()
      bos.size shouldEqual 5
      // We are not allocating 2GB of data here... so this will throw an exception later
      val input = ByteString(bos.toByteArray) ++ ByteString(Array.fill[Byte](100)(1))

      val result = Source.single(input)
        // The tested stage will get stuck on pull(), so we break it with a failure
        .concat(Source.failed(RuntimeException("test")))
        .via(JellyIo.protobufFraming(Int.MaxValue))
        .runWith(Sink.seq)

      val ex = result.failed.futureValue
      ex shouldBe a[RuntimeException]
      ex.getMessage shouldEqual "test"
    }

    "not accept delimiter length of more than 5 bytes" in {
      val bos = ByteArrayOutputStream()
      val cos = CodedOutputStream.newInstance(bos)
      cos.writeInt64NoTag(1L << 60)
      cos.flush()
      bos.size shouldEqual 9
      val input = ByteString(bos.toByteArray)

      val resultFail = Source.single(input)
        .via(JellyIo.protobufFraming(1000))
        .runWith(Sink.seq)

      val ex = resultFail.failed.futureValue
      ex shouldBe a[FramingException]
      ex.getMessage should include ("Delimiting varint too long (over 5 bytes)")
    }
  }

  "delimitedFromStream" should {
    for (name, numFrames, frameSize, inputChunkSize) <- framingTestCases do
      s"work for $name" in {
        val dataSizes = collection.mutable.ListBuffer[Int]()
        val byteStrings = Iterator
          .continually(r.nextInt(frameSize + 2) + frameSize - (frameSize / 2))
          .take(numFrames)
          .map(size => {
            val dataSize = Math.max(size - 6, 1)
            dataSizes += dataSize
            val nameEntry = RdfNameEntry.newInstance()
              .setValue("a".repeat(dataSize))
            val row = RdfStreamRow.newInstance().setName(nameEntry)
            val frame = RdfStreamFrame.newInstance().addRows(row)
            val frameSize = frame.getSerializedSize
            val prefixLen = CodedOutputStream.computeInt32SizeNoTag(frameSize)
            val data = new Array[Byte](frameSize + prefixLen)
            val output = CodedOutputStream.newInstance(data)
            output.writeInt32NoTag(frameSize)
            frame.writeTo(output)
            output.flush()
            ByteString(data)
          })
          .reduce((a, b) => a ++ b)
          .grouped(inputChunkSize)
          .toSeq

        val result = Source(byteStrings)
          .via(JellyIo.fromByteStreamDelimited)
          .runWith(Sink.seq)
          .futureValue

        result.size shouldEqual numFrames
        for (frame, expectedSize) <- result.zip(dataSizes) do {
          frame.getRows.size() shouldEqual 1
          val name = frame.getRows.iterator().next().getName
          name.getValue.length shouldEqual expectedSize
        }
      }
  }
