package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.MockProtoEncoder
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*

  // Test body
  "a ProtoEncoder" should {
    "encode triple statements" in {
      val encoder = MockProtoEncoder(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.TRIPLES)
      )
      val encoded = Triples1.mrl.flatMap(triple => encoder.addTripleStatement(triple).toSeq)
      assertEncoded(encoded, Triples1.encoded(encoder.options))
    }

    "encode quad statements" in {
      val encoder = MockProtoEncoder(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS)
      )
      val encoded = Quads1.mrl.flatMap(quad => encoder.addQuadStatement(quad).toSeq)
      assertEncoded(encoded, Quads1.encoded(encoder.options))
    }

    "encode quad statements (repeated default graph)" in {
      val encoder = MockProtoEncoder(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS)
      )
      val encoded = Quads2RepeatDefault.mrl.flatMap(quad => encoder.addQuadStatement(quad).toSeq)
      assertEncoded(encoded, Quads2RepeatDefault.encoded(encoder.options))
    }

    "encode graphs" in {
      val encoder = MockProtoEncoder(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS)
      )
      val encoded = Graphs1.mrl.flatMap((graphName, triples) => Seq(
        encoder.startGraph(graphName).toSeq,
        triples.flatMap(triple => encoder.addTripleStatement(triple).toSeq),
        encoder.endGraph().toSeq
      ).flatten)
      assertEncoded(encoded, Graphs1.encoded(encoder.options))
    }

    "not allow to end a graph before starting one" in {
      val encoder = MockProtoEncoder(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.QUADS)
      )
      val error = intercept[RdfProtoSerializationError] {
        encoder.endGraph()
      }
      error.getMessage should include ("Cannot end a delimited graph before starting one")
    }

    "not allow to use quoted triples as the graph name" in {
      val encoder = MockProtoEncoder(
        JellyOptions.smallGeneralized.withPhysicalType(PhysicalStreamType.GRAPHS)
      )
      val error = intercept[RdfProtoSerializationError] {
        encoder.startGraph(TripleNode(
          Triple(BlankNode("S"), BlankNode("P"), BlankNode("O"))
        ))
      }
      error.getMessage should include ("Cannot encode graph node")
    }
  }
