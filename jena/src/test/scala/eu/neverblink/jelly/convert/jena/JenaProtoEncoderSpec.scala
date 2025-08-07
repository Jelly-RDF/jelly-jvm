package eu.neverblink.jelly.convert.jena

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.*
import eu.neverblink.jelly.core.ProtoEncoder.Params
import eu.neverblink.jelly.core.memory.RowBuffer
import eu.neverblink.jelly.core.proto.v1.*
import org.apache.jena.graph.NodeFactory
import org.apache.jena.sparql.core.Quad
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/** Test the handling of the many ways to represent the default graph in Jena.
  */
class JenaProtoEncoderSpec extends AnyWordSpec, Matchers, JenaTest:

  private val encodedDefaultGraph = RdfStreamRow.newInstance
    .setGraphStart(
      RdfGraphStart.newInstance.setGraph(RdfDefaultGraph.EMPTY),
    )

  "JenaProtoEncoder" should {
    "encode a null graph node as default graph" in {
      val buffer = RowBuffer.newLazyImmutable()
      val encoder = JenaConverterFactory.getInstance().encoder(
        Params.of(JellyOptions.SMALL_GENERALIZED, false, buffer),
      )
      encoder.handleGraphStart(null)
      buffer.size should be(2)
      buffer.getRows.get(1) should be(encodedDefaultGraph)
    }

    "encode an explicitly named default graph as default graph" in {
      val buffer = RowBuffer.newLazyImmutable()
      val encoder = JenaConverterFactory.getInstance().encoder(
        Params.of(JellyOptions.SMALL_GENERALIZED, false, buffer),
      )
      encoder.handleGraphStart(Quad.defaultGraphIRI)
      buffer.size should be(2)
      buffer.getRows.get(1) should be(encodedDefaultGraph)
    }

    "encode a generated default graph as default graph" in {
      val buffer = RowBuffer.newLazyImmutable()
      val encoder = JenaConverterFactory.getInstance().encoder(
        Params.of(JellyOptions.SMALL_GENERALIZED, false, buffer),
      )
      encoder.handleGraphStart(Quad.defaultGraphNodeGenerated)
      buffer.size should be(2)
      buffer.getRows.get(1) should be(encodedDefaultGraph)
    }

    "encode an xsd:string literal as a simple literal (no datatype)" in {
      val buffer = RowBuffer.newLazyImmutable()
      val encoder = JenaConverterFactory.getInstance().encoder(
        Params.of(JellyOptions.SMALL_GENERALIZED, false, buffer),
      )
      encoder.handleQuad(
        NodeFactory.createBlankNode(),
        NodeFactory.createBlankNode(),
        NodeFactory.createLiteralString("test"),
        NodeFactory.createLiteralString("test"),
      )
      buffer.size should be(2) // 1 for the options, 1 for the triple
      val row = buffer.getRows.get(1)
      row.getQuad.getObject.asInstanceOf[RdfLiteral].getLiteralKind should be(null)
      row.getQuad.getGraph.asInstanceOf[RdfLiteral].getLiteralKind should be(null)
    }
  }
