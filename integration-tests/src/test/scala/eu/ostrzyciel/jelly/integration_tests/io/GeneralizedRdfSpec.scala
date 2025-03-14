package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.pekko.actor.ActorSystem
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.reflect.ClassTag

/**
 * Separate tests for handling generalized RDF.
 *
 * Normally this would be included with the IoSerDesSpec, but RDF libraries have very patch support
 * for generalized RDF.
 */
class GeneralizedRdfSpec extends AnyWordSpec, Matchers:
  given ActorSystem = ActorSystem("test")

  def frameAsDelimited(frame: RdfStreamFrame): Array[Byte] =
    val os = new ByteArrayOutputStream()
    frame.writeDelimitedTo(os)
    os.toByteArray

  private val frameTriples = RdfStreamFrame(rows = Seq(
    RdfStreamRow(JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES)),
    RdfStreamRow(RdfTriple(
      RdfLiteral("s"),
      RdfLiteral("p"),
      RdfLiteral("o"),
    ))
  ))
  private val bytesTriples = frameAsDelimited(frameTriples)

  private val frameQuads = RdfStreamFrame(rows = Seq(
    RdfStreamRow(JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS)),
    RdfStreamRow(RdfQuad(
      RdfLiteral("s"),
      RdfLiteral("p"),
      RdfLiteral("o"),
      RdfLiteral("g"),
    ))
  ))
  private val bytesQuads = frameAsDelimited(frameQuads)

  private val frameGraphs = RdfStreamFrame(rows = Seq(
    RdfStreamRow(JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS)),
    RdfStreamRow(RdfGraphStart(
      RdfLiteral("g"),
    )),
    RdfStreamRow(RdfTriple(
      RdfLiteral("s"),
      RdfLiteral("p"),
      RdfLiteral("o"),
    )),
    RdfStreamRow(RdfGraphEnd.defaultInstance),
  ))
  private val bytesGraphs = frameAsDelimited(frameGraphs)
  
  def roundTripTests[TModel : Measure, TDataset : Measure](
    impl: NativeSerDes[TModel, TDataset]
  ): Unit =
    val mm = summon[Measure[TModel]]
    val md = summon[Measure[TDataset]]

    "round-trip triples" in {
      val triples = impl.readTriplesJelly(ByteArrayInputStream(bytesTriples), None)
      mm.size(triples) should be(1)
      val os = new ByteArrayOutputStream()
      impl.writeTriplesJelly(os, triples, None, 100)
      os.size() should be > 10
      val triples2 = impl.readTriplesJelly(ByteArrayInputStream(os.toByteArray), None)
      mm.size(triples2) should be(1)
    }

    "round-trip quads" in {
      val quads = impl.readQuadsJelly(ByteArrayInputStream(bytesQuads), None)
      md.size(quads) should be(1)
      val os = new ByteArrayOutputStream()
      impl.writeQuadsJelly(os, quads, None, 100)
      os.size() should be > 10
      val quads2 = impl.readQuadsJelly(ByteArrayInputStream(os.toByteArray), None)
      md.size(quads2) should be(1)
    }

    "round-trip graphs" in {
      val graphs = impl.readQuadsJelly(ByteArrayInputStream(bytesGraphs), None)
      md.size(graphs) should be(1)
      val os = new ByteArrayOutputStream()
      impl.writeQuadsJelly(os, graphs, None, 100)
      os.size() should be > 10
      val graphs2 = impl.readQuadsJelly(ByteArrayInputStream(os.toByteArray), None)
      md.size(graphs2) should be(1)
    }

  def parsingFailureTests[TErr <: AnyRef : ClassTag](impl: NativeSerDes[?, ?]): Unit =
    "fail to parse triples" in {
      intercept[TErr] {
        impl.readTriplesJelly(ByteArrayInputStream(bytesTriples), None)
      }
    }

    "fail to parse quads" in {
      intercept[TErr] {
        impl.readQuadsJelly(ByteArrayInputStream(bytesQuads), None)
      }
    }

    "fail to parse graphs" in {
      intercept[TErr] {
        impl.readQuadsJelly(ByteArrayInputStream(bytesGraphs), None)
      }
    }

  "Jena RIOT implementation" should {
    roundTripTests(JenaSerDes)
  }

  "Jena RIOT streaming implementation" should {
    roundTripTests(JenaStreamSerDes)
  }

  "Jena reactive streaming implementation" should {
    roundTripTests(JenaReactiveSerDes())
  }

  "RDF4J implementation" should {
    parsingFailureTests[ClassCastException](Rdf4jSerDes)
  }

  "RDF4J reactive implementation" should {
    parsingFailureTests[ClassCastException](Rdf4jReactiveSerDes())
  }
  
  "Titanium implementation" should {
    parsingFailureTests[ClassCastException](TitaniumSerDes)
  }
