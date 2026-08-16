package eu.neverblink.jelly.core.sparql

import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.RdfIri
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.helpers.{CustomEncoderConverter, MockSparqlConverterFactory}
import eu.neverblink.jelly.core.sparql.internal.SparqlEncoderImpl
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.lang.reflect.InvocationTargetException
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

@experimental
class SparqlEncoderSpec extends AnyWordSpec, Matchers:

  private def encoder(options: SparqlResultsOptions = JellySparqlOptions.SMALL) =
    MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))

  /** An encoder whose converter does whatever the test tells it to. */
  private def customEncoder(f: (eu.neverblink.jelly.core.NodeEncoder[Node], Node) => Object) =
    SparqlEncoderImpl[Node](
      CustomEncoderConverter(f),
      SparqlEncoder.Params.of(JellySparqlOptions.SMALL),
    )

  "SparqlEncoder" should {
    "reject setting the variables twice" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.setVariables(Seq("y").asJava)
      }
      error.getMessage should include("already been set")
    }

    "reject ending a frame before the variables are set" in {
      val error = intercept[RdfProtoSerializationError] {
        encoder().endFrame()
      }
      error.getMessage should include("Variables must be set")
    }

    "reject more rows in one frame than the layout encoding can address" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      // Pretend the frame is already full – appending 2^27 rows for real would take forever
      val rowCount = classOf[SparqlEncoderImpl[?]].getDeclaredField("rowCount")
      rowCount.setAccessible(true)
      rowCount.setInt(e, (1 << 27) - 1)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](Iri("https://test.org/a")))
      }
      error.getMessage should include("cannot hold more than 134217727 rows")
    }

    "reject quoted triples appended as a buffer appender" in {
      // Not reachable through the converter API (the node encoder rejects them first), but the
      // encoder implements RdfBufferAppender, so the method is part of its surface.
      val e = encoder()
      val error = intercept[RdfProtoSerializationError] {
        e.appendQuotedTriple(
          Iri("https://test.org/a"),
          Iri("https://test.org/b"),
          Iri("https://test.org/c"),
        )
      }
      error.getMessage should include("quoted triples are not supported")
    }

    "not support building triples or quads" in {
      val e = encoder()
      for name <- Seq("newTriple", "newQuad") do
        val method = classOf[SparqlEncoder[?]].getDeclaredMethod(name)
        method.setAccessible(true)
        val error = intercept[InvocationTargetException] {
          method.invoke(e)
        }
        error.getCause shouldBe a[UnsupportedOperationException]
    }
  }

  "the column node encoder" should {
    "compress IRIs against the state of the current column" in {
      // Exercises all four combinations of (same prefix, next name) in one column.
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      for iri <- Seq("https://a.org/x1", "https://a.org/x2", "https://a.org/x1", "https://b.org/z")
      do e.appendRow(Array[Node](Iri(iri)))
      val values = e.endFrame().getIriColumns.asScala.head.getValues.asScala.toSeq

      // First IRI of the column: new prefix, name 1 (which is "the next one" from the initial 0)
      values(0).getPrefixId shouldBe 1
      values(0).getNameId shouldBe 0
      // Same prefix, next name: fully inferred, nothing on the wire
      values(1).getPrefixId shouldBe 0
      values(1).getNameId shouldBe 0
      // Same prefix, but back to name 1: the name must be stated
      values(2).getPrefixId shouldBe 0
      values(2).getNameId shouldBe 1
      // New prefix and a non-consecutive name: both must be stated
      values(3).getPrefixId shouldBe 2
      values(3).getNameId shouldBe 3
    }

    "restart the IRI inference state in every frame" in {
      val e = encoder()
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.endFrame()
      // Same IRI again, in a new frame: it must state its prefix, as the decoder resets per column
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      val value = e.endFrame().getIriColumns.asScala.head.getValues.asScala.head
      value.getPrefixId shouldBe 1
    }

    "expose uncompressed IRIs to converters that ask for them" in {
      val e = customEncoder((enc, node) =>
        node match
          case Iri(iri) => enc.makeIriRaw(iri)
          case other => throw RuntimeException(s"unexpected $other"),
      )
      e.setVariables(Seq("x").asJava)
      e.appendRow(Array[Node](Iri("https://a.org/x1")))
      e.appendRow(Array[Node](Iri("https://a.org/x2")))
      val values = e.endFrame().getIriColumns.asScala.head.getValues.asScala.toSeq
      // Raw IRIs always carry their real identifiers – no 0-compression
      values.map(v => (v.getPrefixId, v.getNameId)) shouldBe Seq((1, 1), (1, 2))
    }

    "reject the default graph as a binding" in {
      val e = customEncoder((enc, _) => enc.makeDefaultGraph())
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](DefaultGraphNode()))
      }
      error.getMessage should include("default graph is not a valid SPARQL result binding")
    }

    "reject unsupported term types" in {
      val e = customEncoder((_, _) => Integer.valueOf(42))
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](Iri("https://test.org/a")))
      }
      error.getMessage should include("Unsupported term type")
      error.getMessage should include("java.lang.Integer")
    }

    "reject a converter that encodes nothing" in {
      val e = customEncoder((_, _) => null)
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        e.appendRow(Array[Node](Iri("https://test.org/a")))
      }
      error.getMessage should include("Unsupported term type in SPARQL results: null")
    }
  }

  "the lookup table guard" should {
    "reject a frame whose prefixes do not fit the prefix table" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(1000)
        .setMaxPrefixTableSize(2)
        .setMaxDatatypeTableSize(8)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        for i <- 1 to 10 do e.appendRow(Array[Node](Iri(s"https://ns$i.org/thing")))
      }
      error.getMessage should include("prefix lookup table is too small")
    }

    "reject a frame whose datatypes do not fit the datatype table" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(1000)
        .setMaxPrefixTableSize(64)
        .setMaxDatatypeTableSize(2)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      val error = intercept[RdfProtoSerializationError] {
        for i <- 1 to 10 do
          e.appendRow(Array[Node](DtLiteral("v", Datatype(s"https://test.org/dt$i"))))
      }
      error.getMessage should include("datatype lookup table is too small")
    }

    "allow reusing the same lookup entries in the next frame" in {
      val options = SparqlResultsOptions
        .newInstance()
        .setMaxNameTableSize(8)
        .setMaxPrefixTableSize(2)
        .setMaxDatatypeTableSize(2)
      val e = encoder(options)
      e.setVariables(Seq("x").asJava)
      // Each frame stays within the table size, so evictions across frames are fine
      for i <- 1 to 20 do
        e.appendRow(Array[Node](Iri(s"https://test.org/thing$i")))
        e.endFrame().getRowCount shouldBe 1
    }
  }
