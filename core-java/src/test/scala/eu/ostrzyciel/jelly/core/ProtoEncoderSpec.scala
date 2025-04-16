package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.*
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*
  import ProtoEncoder.Params as Pep

  // Test body
  "a ProtoEncoder" should {
    "encode triple statements" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.TRIPLES)
      ))
      val encoded = Triples1.mrl.flatMap(triple => encoder.addTripleStatement(triple).toSeq)
      assertEncoded(encoded, Triples1.encoded(encoder.options.withVersion(Constants.protoVersion_1_0_x)))
    }

    "encode triple statements with namespace declarations" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.TRIPLES),
        enableNamespaceDeclarations = true,
      ))
      val encoded = Triples2NsDecl.mrl.flatMap {
        case t: Triple => encoder.addTripleStatement(t).toSeq
        case ns: NamespaceDeclaration => encoder.declareNamespace(ns.prefix, ns.iri).toSeq
      }
      assertEncoded(encoded, Triples2NsDecl.encoded(encoder.options))
    }

    "encode triple statements with ns decls and an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.TRIPLES),
        enableNamespaceDeclarations = true, Some(buffer)
      ))
      for triple <- Triples2NsDecl.mrl do
        val result = triple match
          case t: Triple => encoder.addTripleStatement(t)
          case ns: NamespaceDeclaration => encoder.declareNamespace(ns.prefix, ns.iri)
        // external buffer – nothing should be returned directly
        result.size should be (0)

      assertEncoded(buffer.toSeq, Triples2NsDecl.encoded(encoder.options))
    }

    "encode quad statements" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.QUADS)
      ))
      val encoded = Quads1.mrl.flatMap(quad => encoder.addQuadStatement(quad).toSeq)
      assertEncoded(encoded, Quads1.encoded(encoder.options.withVersion(Constants.protoVersion_1_0_x)))
    }

    "encode quad statements with an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.QUADS),
        false, Some(buffer)
      ))
      for quad <- Quads1.mrl do
        val result = encoder.addQuadStatement(quad)
        // external buffer – nothing should be returned directly
        result.size should be (0)

      assertEncoded(buffer.toSeq, Quads1.encoded(encoder.options.withVersion(Constants.protoVersion_1_0_x)))
    }

    "encode quad statements (repeated default graph)" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.QUADS)
      ))
      val encoded = Quads2RepeatDefault.mrl.flatMap(quad => encoder.addQuadStatement(quad).toSeq)
      assertEncoded(encoded, Quads2RepeatDefault.encoded(encoder.options.withVersion(Constants.protoVersion_1_0_x)))
    }

    "encode graphs" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.GRAPHS)
      ))
      val encoded = Graphs1.mrl.flatMap((graphName, triples) => Seq(
        encoder.startGraph(graphName).toSeq,
        triples.flatMap(triple => encoder.addTripleStatement(triple).toSeq),
        encoder.endGraph().toSeq
      ).flatten)
      assertEncoded(encoded, Graphs1.encoded(encoder.options.withVersion(Constants.protoVersion_1_0_x)))
    }

    "encode graphs with an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.GRAPHS),
        false, Some(buffer)
      ))
      for (graphName, triples) <- Graphs1.mrl do
        val start = encoder.startGraph(graphName)
        start.size should be (0)
        for triple <- triples do
          val result = encoder.addTripleStatement(triple)
          result.size should be (0)
        val end = encoder.endGraph()
        end.size should be (0)

      assertEncoded(buffer.toSeq, Graphs1.encoded(encoder.options.withVersion(Constants.protoVersion_1_0_x)))
    }

    "not allow to end a graph before starting one" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.QUADS)
      ))
      val error = intercept[RdfProtoSerializationError] {
        encoder.endGraph()
      }
      error.getMessage should include ("Cannot end a delimited graph before starting one")
    }

    "not allow to use quoted triples as the graph name" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.GRAPHS)
      ))
      val error = intercept[RdfProtoSerializationError] {
        encoder.startGraph(TripleNode(
          Triple(BlankNode("S"), BlankNode("P"), BlankNode("O"))
        ))
      }
      error.getMessage should include ("Cannot encode graph node")
    }

    "not allow to use namespace declarations if they are not enabled" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.TRIPLES),
        enableNamespaceDeclarations = false,
      ))
      val error = intercept[RdfProtoSerializationError] {
        encoder.declareNamespace("test", "https://test.org/test/")
      }
      error.getMessage should include ("Namespace declarations are not enabled in this stream")
    }

    "return options with the correct version" in {
      val encoder = MockConverterFactory.encoder(Pep(
        JellyOptions.SMALL_GENERALIZED.withPhysicalType(PhysicalStreamType.TRIPLES)
      ))
      encoder.options.version should be (Constants.protoVersion_1_0_x)
    }
  }
