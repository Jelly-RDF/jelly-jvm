package eu.ostrzyciel.jelly.convert.jena

import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import eu.ostrzyciel.jelly.core.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.apache.jena.sparql.core.Quad
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec


/**
 * Test the handling of the many ways to represent the default graph in Jena.
 */
class JenaProtoEncoderSpec extends AnyWordSpec, Matchers, JenaTest:
  private val encodedDefaultGraph = RdfStreamRow(
    RdfGraphStart(RdfDefaultGraph())
  )
  
  "JenaProtoEncoder" should {
    "encode a null graph node as default graph" in {
      val encoder = JenaProtoEncoder(JellyOptions.smallGeneralized)
      val rows = encoder.startGraph(null).toSeq
      rows.size should be (2)
      rows(1) should be (encodedDefaultGraph)
    }
    
    "encode an explicitly named default graph as default graph" in {
      val encoder = JenaProtoEncoder(JellyOptions.smallGeneralized)
      val rows = encoder.startGraph(Quad.defaultGraphIRI).toSeq
      rows.size should be (2)
      rows(1) should be (encodedDefaultGraph)
    }
    
    "encode a generated default graph as default graph" in {
      val encoder = JenaProtoEncoder(JellyOptions.smallGeneralized)
      val rows = encoder.startGraph(Quad.defaultGraphNodeGenerated).toSeq
      rows.size should be (2)
      rows(1) should be (encodedDefaultGraph)
    }
  }
