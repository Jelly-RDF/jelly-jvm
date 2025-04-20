package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.helpers.*
import eu.ostrzyciel.jelly.core.helpers.Mrl.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*
  import ProtoEncoder.Params as Pep

  // Test body
  "a ProtoEncoder" should {
    "encode triple statements" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))
      Triples1.mrl.foreach(triple => encoder.addTripleStatement(triple))
      assertEncoded(buffer.toSeq, Triples1.encoded(options))
    }

    "encode triple statements with ns decls and an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = true,
        appendableRowBuffer = buffer.asJava
      ))

      for triple <- Triples2NsDecl.mrl do
        triple match
          case t: Triple => encoder.addTripleStatement(t)
          case ns: NamespaceDeclaration => encoder.declareNamespace(ns.prefix, ns.iri)

      assertEncoded(buffer.toSeq, Triples2NsDecl.encoded(options))
    }

    "encode quad statements" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      Quads1.mrl.foreach(quad => encoder.addQuadStatement(quad))
      assertEncoded(buffer.toSeq, Quads1.encoded(options))
    }

    "encode quad statements with an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      for quad <- Quads1.mrl do
        encoder.addQuadStatement(quad)

      assertEncoded(buffer.toSeq, Quads1.encoded(options))
    }

    "encode quad statements (repeated default graph)" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      Quads2RepeatDefault.mrl.foreach(quad => encoder.addQuadStatement(quad))
      assertEncoded(buffer.toSeq, Quads2RepeatDefault.encoded(options))
    }

    "encode graphs with an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      for (graphName, triples) <- Graphs1.mrl do
        encoder.startGraph(graphName)
        for triple <- triples do
          encoder.addTripleStatement(triple)
        encoder.endGraph()

      assertEncoded(buffer.toSeq, Graphs1.encoded(options))
    }

    "not allow to end a graph before starting one" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_QUADS)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.endGraph()
      }

      error.getMessage should include ("Cannot end a delimited graph before starting one")
    }

    "not allow to use quoted triples as the graph name" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_GRAPHS)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.startGraph(Triple(BlankNode("S"), BlankNode("P"), BlankNode("O")))
      }

      error.getMessage should include ("Cannot encode graph node")
    }

    "not allow to use namespace declarations if they are not enabled" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.toBuilder
        .setPhysicalType(PhysicalStreamType.PHYSICAL_STREAM_TYPE_TRIPLES)
        .build()

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.declareNamespace("test", "https://test.org/test/")
      }

      error.getMessage should include ("Namespace declarations are not enabled in this stream")
    }
  }
