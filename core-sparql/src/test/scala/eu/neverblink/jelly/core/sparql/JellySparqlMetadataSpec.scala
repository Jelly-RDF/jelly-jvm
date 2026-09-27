package eu.neverblink.jelly.core.sparql

import com.google.protobuf.ByteString
import eu.neverblink.jelly.core.{RdfProtoDeserializationError, RdfProtoSerializationError}
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsFrame
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class JellySparqlMetadataSpec extends AnyWordSpec, Matchers:

  private def entry(key: String, value: String) =
    SparqlResultsFrame.MetadataEntry.newInstance().setKey(key).setValue(
      ByteString.copyFromUtf8(value),
    )

  "encodeLinks and decodeLinks" should {
    "round-trip any number of links" in {
      for links <- Seq(Seq(), Seq("https://a.org/"), Seq("https://a.org/", "https://b.org/x")) do
        JellySparqlMetadata.decodeLinks(
          JellySparqlMetadata.encodeLinks(links.asJava),
        ).asScala shouldBe
          links
    }

    "separate the links with LF" in {
      JellySparqlMetadata
        .encodeLinks(Seq("https://a.org/", "https://b.org/").asJava)
        .toStringUtf8 shouldBe "https://a.org/\nhttps://b.org/"
    }

    "reject a link containing LF" in {
      intercept[RdfProtoSerializationError] {
        JellySparqlMetadata.encodeLinks(Seq("https://a.org/\nx").asJava)
      }.getMessage should include("must not contain the LF character")
    }

    "reject a value that is not valid UTF-8" in {
      intercept[RdfProtoDeserializationError] {
        JellySparqlMetadata.decodeLinks(ByteString.copyFrom(Array[Byte](0xff.toByte)))
      }.getMessage should include("not valid UTF-8")
    }
  }

  "addMetadata" should {
    def encoder() =
      val e = helpers.MockSparqlConverterFactory.encoder(
        SparqlEncoder.Params.of(JellySparqlOptions.SMALL),
      )
      e.setVariables(Seq("x").asJava)
      e

    "add entries to a frame from the encoder, keeping its size right" in {
      val e = encoder()
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      val frame = e.endFrame()
      JellySparqlMetadata.addMetadata(frame, "a", ByteString.copyFromUtf8("1"))
      JellySparqlMetadata.addLinks(frame, Seq("https://a.org/").asJava)
      frame.getSerializedSize shouldBe frame.toByteArray.length
      val parsed = SparqlResultsFrame.parseFrom(frame.toByteArray)
      parsed.getMetadata.asScala.map(e => e.getKey -> e.getValue.toStringUtf8).toSeq shouldBe
        Seq("a" -> "1", "link" -> "https://a.org/")
      parsed.getRowCount shouldBe 1
    }

    "not carry the entries over to the next frame" in {
      val e = encoder()
      JellySparqlMetadata.addLinks(e.endFrame(), Seq("https://a.org/").asJava)
      e.endStream().getMetadata.size shouldBe 0
    }

    "work on a boolean result frame" in {
      val frame = SparqlEncoder.askResultFrame(JellySparqlOptions.SMALL, true)
      JellySparqlMetadata.addLinks(frame, Seq("https://a.org/").asJava)
      frame.getSerializedSize shouldBe frame.toByteArray.length
      JellySparqlMetadata
        .getLinks(SparqlResultsFrame.parseFrom(frame.toByteArray))
        .asScala shouldBe Seq("https://a.org/")
    }

    "work on the frame that ends a stream after a failed row" in {
      val e = encoder()
      intercept[RdfProtoSerializationError] {
        e.appendRow(
          Array[Node](
            TripleNode(Iri("https://a.org/s"), Iri("https://a.org/p"), Iri("https://a.org/o")),
          ),
        )
      }
      val frame = e.endStream("failed")
      JellySparqlMetadata.addLinks(frame, Seq("https://a.org/").asJava)
      frame.getSerializedSize shouldBe frame.toByteArray.length
    }
  }

  "getLinks" should {
    "return null if the frame has no links" in {
      JellySparqlMetadata.getLinks(SparqlResultsFrame.newInstance()) shouldBe null
      JellySparqlMetadata.getLinks(
        SparqlResultsFrame.newInstance().addMetadata(entry("other", "x")),
      ) shouldBe null
    }

    "return the links of the frame" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .addMetadata(entry("other", "x"))
        .addMetadata(entry("link", "https://a.org/\nhttps://b.org/"))
      JellySparqlMetadata.getLinks(frame).asScala shouldBe Seq("https://a.org/", "https://b.org/")
    }

    "take the last value of a repeated key" in {
      val frame = SparqlResultsFrame
        .newInstance()
        .addMetadata(entry("link", "https://a.org/"))
        .addMetadata(entry("link", "https://b.org/"))
      JellySparqlMetadata.getLinks(frame).asScala shouldBe Seq("https://b.org/")
    }
  }
