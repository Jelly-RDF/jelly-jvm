package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsOptions}
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class JellySparqlIoUtilsSpec extends AnyWordSpec, Matchers:

  private def frameFor(options: SparqlResultsOptions): SparqlResultsFrame =
    val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
    encoder.setVariables(Seq("x").asJava)
    encoder.appendRow(Array[Node](Iri("https://test.org/ns#a")))
    encoder.endFrame()

  private def nonDelimited(options: SparqlResultsOptions): Array[Byte] =
    val out = ByteArrayOutputStream()
    frameFor(options).writeTo(out)
    out.toByteArray

  private def delimited(options: SparqlResultsOptions): Array[Byte] =
    val out = ByteArrayOutputStream()
    frameFor(options).writeDelimitedTo(out)
    out.toByteArray

  private def isDelimited(bytes: Array[Byte]): Boolean =
    JellySparqlIoUtils.autodetectDelimiting(ByteArrayInputStream(bytes)).isDelimited

  /** Reads the stream back the way the readers do, to prove the detection is actually usable. */
  private def readBack(bytes: Array[Byte]): Seq[Seq[Node]] =
    val collector = ResultsCollector()
    val decoder = MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.MAX)
    val response = JellySparqlIoUtils.autodetectDelimiting(ByteArrayInputStream(bytes))
    val in = response.newInput
    if response.isDelimited then
      var frame = SparqlResultsFrame.parseDelimitedFrom(in)
      while frame != null do
        decoder.ingestFrame(frame)
        frame = SparqlResultsFrame.parseDelimitedFrom(in)
    else decoder.ingestFrame(SparqlResultsFrame.parseFrom(in))
    collector.rows.toSeq

  /** Table sizes chosen so that the serialized options span several byte lengths. */
  private val optionSweep =
    for
      nameTableSize <- Seq(128, 256, 1024, 8192, 16384)
      prefixTableSize <- Seq(0, 64, 1024, 4096)
    yield SparqlResultsOptions
      .newInstance()
      .setMaxNameTableSize(nameTableSize)
      .setMaxPrefixTableSize(prefixTableSize)
      .setMaxDatatypeTableSize(64)

  "JellySparqlIoUtils.autodetectDelimiting" should {
    "cover the options size that collides with the options tag" in {
      // A 10-byte options message makes a non-delimited frame start with 0A 0A, which is also how
      // a delimited stream whose first frame is 10 bytes long starts. Make sure we cover this case.
      val sizes = optionSweep.map(_.clone().setVersion(JellySparqlConstants.PROTO_VERSION))
        .map(_.getSerializedSize)
      sizes should contain(10)
    }

    for options <- optionSweep do
      val label =
        s"name=${options.getMaxNameTableSize}, prefix=${options.getMaxPrefixTableSize}"

      s"detect a non-delimited frame with $label" in {
        isDelimited(nonDelimited(options)) shouldBe false
        readBack(nonDelimited(options)) shouldBe Seq(Seq(Iri("https://test.org/ns#a")))
      }

      s"detect a delimited stream with $label" in {
        isDelimited(delimited(options)) shouldBe true
        readBack(delimited(options)) shouldBe Seq(Seq(Iri("https://test.org/ns#a")))
      }

    "treat a truncated stream as non-delimited" in {
      isDelimited(Array[Byte](0x0a, 0x0a)) shouldBe false
    }
  }
