package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.RdfVersion
import eu.neverblink.jelly.core.proto.v1.sparql.*
import eu.neverblink.jelly.core.sparql.helpers.MockSparqlConverterFactory
import eu.neverblink.jelly.core.{RdfProtoDeserializationError, RdfProtoSerializationError}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** PUNCTUATED streams (sequences of result sets), and the stream type checks. */
class SparqlPunctuatedSpec extends AnyWordSpec, Matchers:

  private def iri(i: Int) = Iri(f"https://test.org/ns#term$i")

  private val punctuatedOptions =
    JellySparqlOptions.SMALL.clone().setStreamType(SparqlStreamType.PUNCTUATED)

  private val punctuatedSupported =
    JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS.clone().setStreamType(SparqlStreamType.PUNCTUATED)

  /** Records the handler calls, in order. */
  private final class EventCollector extends SparqlResultsHandler[Node]:
    val events: ListBuffer[String] = ListBuffer()

    override def handleVariables(vars: util.List[String]): Unit =
      events += s"vars(${vars.asScala.mkString(",")})"

    override def createRowBuffer(size: Int): Array[Object & Node] =
      new Array[Node](size).asInstanceOf[Array[Object & Node]]

    override def handleRow(row: Array[Object & Node]): Unit =
      events += s"row(${row.map(String.valueOf).mkString(",")})"

    override def handleAskResult(value: Boolean): Unit = events += s"ask($value)"

    override def handleTrailer(error: String): Unit = events += s"trailer($error)"

  private def decoder(supported: SparqlResultsOptions = punctuatedSupported) =
    val collector = EventCollector()
    (collector, MockSparqlConverterFactory.decoder(collector, supported))

  private def ingestAll(decoder: SparqlDecoder, frames: Seq[SparqlResultsFrame]): Unit =
    // Round-trip through the serialized form, as the encoder's frames point at its buffers
    for frame <- frames do decoder.ingestFrame(SparqlResultsFrame.parseFrom(frame.toByteArray))

  private def serialized(frame: SparqlResultsFrame): SparqlResultsFrame =
    SparqlResultsFrame.parseFrom(frame.toByteArray)

  private def header(names: String*): Seq[SparqlVariable] =
    names.zipWithIndex.map((n, i) => SparqlVariable.newInstance().setName(n).setColumnIndex(i))

  /** A frame of the given variables with no rows (and so, no columns). */
  private def emptyFrame(names: String*): SparqlResultsFrame.Mutable =
    val frame = SparqlResultsFrame.newInstance()
    header(names*).foreach(frame.addVariables)
    frame

  private def trailer = SparqlResultsTrailer.newInstance()

  "Jelly-SPARQL encoder and decoder" should {
    "round-trip a sequence of result sets, keeping the lookups between them" in {
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(punctuatedOptions))
      val frames = ListBuffer[SparqlResultsFrame]()
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      encoder.appendRow(Array[Node](iri(2)))
      frames += serialized(encoder.endResultSet())
      frames += serialized(encoder.askResult(true))
      encoder.setVariables(Seq("y", "z").asJava)
      encoder.appendRow(Array[Node](iri(1), null))
      frames += serialized(encoder.endFrame())
      encoder.appendRow(Array[Node](null, SimpleLiteral("a")))
      frames += serialized(encoder.endResultSet())

      frames.map(_.getOptions != null) shouldBe Seq(true, false, false, false)
      frames.map(_.getTrailer != null) shouldBe Seq(true, true, false, true)
      frames(0).getOptions.getStreamType shouldBe SparqlStreamType.PUNCTUATED
      // The first frame of each solution sequence has its header
      frames(2).getVariables.asScala.map(_.getName) shouldBe Seq("y", "z")
      // term1 is still in the name lookup from the first result set
      frames(2).getNames.asScala shouldBe empty

      val (collector, dec) = decoder()
      ingestAll(dec, frames.toSeq)
      collector.events shouldBe Seq(
        "vars(x)",
        s"row(${iri(1)})",
        s"row(${iri(2)})",
        "trailer()",
        "ask(true)",
        "trailer()",
        "vars(y,z)",
        s"row(${iri(1)},null)",
        s"row(null,${SimpleLiteral("a")})",
        "trailer()",
      )
    }

    "start a PUNCTUATED stream with a boolean result" in {
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(punctuatedOptions))
      val first = serialized(encoder.askResult(false))
      first.getOptions should not be null
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      val second = serialized(encoder.endStream())
      second.getOptions shouldBe null
      second.getVariables.asScala.map(_.getName) shouldBe Seq("x")

      val (collector, dec) = decoder()
      ingestAll(dec, Seq(first, second))
      collector.events shouldBe Seq(
        "ask(false)",
        "trailer()",
        "vars(x)",
        s"row(${iri(1)})",
        "trailer()",
      )
    }

    "end a result set with an error and go on with the next one" in {
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(punctuatedOptions))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      val first = serialized(encoder.endResultSet("timeout"))
      encoder.setVariables(Seq("x").asJava)
      val second = serialized(encoder.endResultSet())

      val (collector, dec) = decoder()
      ingestAll(dec, Seq(first, second))
      collector.events shouldBe Seq(
        "vars(x)",
        s"row(${iri(1)})",
        "trailer(timeout)",
        "vars(x)",
        "trailer()",
      )
    }
  }

  "SparqlEncoder" should {
    "reject an empty variable name" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      val e = intercept[RdfProtoSerializationError] {
        encoder.setVariables(Seq("x", "").asJava)
      }
      e.getMessage should include("must not be empty")
    }

    "reject an unknown stream type" in {
      val options = JellySparqlOptions.SMALL.clone().setStreamTypeValue(7)
      val e = intercept[RdfProtoSerializationError] {
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
      }
      e.getMessage should include("Unknown stream type")
    }

    "reject endResultSet and askResult in a FLAT stream" in {
      val encoder =
        MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(JellySparqlOptions.SMALL))
      intercept[RdfProtoSerializationError] {
        encoder.askResult(true)
      }.getMessage should include("PUNCTUATED")
      encoder.setVariables(Seq("x").asJava)
      intercept[RdfProtoSerializationError] {
        encoder.endResultSet()
      }.getMessage should include("PUNCTUATED")
    }

    "reject a boolean result in the middle of a result set" in {
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(punctuatedOptions))
      encoder.setVariables(Seq("x").asJava)
      encoder.appendRow(Array[Node](iri(1)))
      intercept[RdfProtoSerializationError] {
        encoder.askResult(true)
      }
    }

    "end the encoder when a result set is ended with an error after a failed row" in {
      val options = punctuatedOptions.clone().setRdfVersion(RdfVersion.RDF_VERSION_1_1)
      val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
      encoder.setVariables(Seq("x").asJava)
      intercept[RdfProtoSerializationError] {
        encoder.appendRow(Array[Node](TripleNode(iri(1), iri(2), iri(3))))
      }
      val frame = encoder.endResultSet("failed")
      frame.getTrailer.getError shouldBe "failed"
      // The lookups no longer match what was written, so no more result sets
      intercept[RdfProtoSerializationError] {
        encoder.setVariables(Seq("x").asJava)
      }
    }
  }

  "SparqlDecoder" should {
    "reject a PUNCTUATED stream unless it is supported" in {
      val (_, dec) = decoder(JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
      val e = intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions))
      }
      e.getMessage should include("PUNCTUATED")
    }

    "reject an unknown stream type" in {
      val (_, dec) = decoder()
      val e = intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(
          emptyFrame("x").setOptions(JellySparqlOptions.SMALL.clone().setStreamTypeValue(2)),
        )
      }
      e.getMessage should include("Unknown stream type")
    }

    "reject a change of the stream type when the options are repeated" in {
      val (_, dec) = decoder()
      dec.ingestFrame(emptyFrame("x").setOptions(JellySparqlOptions.SMALL).setTrailer(trailer))
      val e = intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions))
      }
      e.getMessage should include("stream type must be the same")
    }

    "reject the stream options in a frame that does not start a result set" in {
      val (_, dec) = decoder()
      dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions))
      val e = intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions))
      }
      e.getMessage should include("first frame of a result set")
    }

    "accept repeated stream options in the first frame of a result set" in {
      val (collector, dec) = decoder()
      dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions).setTrailer(trailer))
      dec.ingestFrame(emptyFrame("y").setOptions(punctuatedOptions).setTrailer(trailer))
      collector.events shouldBe Seq("vars(x)", "trailer()", "vars(y)", "trailer()")
    }

    "reject a boolean result in a frame that does not start a result set" in {
      val (_, dec) = decoder()
      dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions))
      intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(
          SparqlResultsFrame.newInstance().setAskResult(SparqlAskResult.newInstance()),
        )
      }
    }

    "reject a frame after a boolean result without a trailer" in {
      val (_, dec) = decoder()
      dec.ingestFrame(
        SparqlResultsFrame
          .newInstance()
          .setOptions(punctuatedOptions)
          .setAskResult(SparqlAskResult.newInstance()),
      )
      intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(emptyFrame("x").setTrailer(trailer))
      }
    }

    "read a trailer-only frame after a trailer as an empty zero-variable result set" in {
      val (collector, dec) = decoder()
      dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions).setTrailer(trailer))
      dec.ingestFrame(SparqlResultsFrame.newInstance().setTrailer(trailer))
      collector.events shouldBe Seq("vars(x)", "trailer()", "vars()", "trailer()")
    }

    "check a restated header against the header of its own result set" in {
      val (_, dec) = decoder()
      dec.ingestFrame(emptyFrame("x").setOptions(punctuatedOptions).setTrailer(trailer))
      dec.ingestFrame(emptyFrame("y"))
      // Restating the header of the first result set is not allowed in the second one
      intercept[RdfProtoDeserializationError] {
        dec.ingestFrame(emptyFrame("x"))
      }
    }
  }
