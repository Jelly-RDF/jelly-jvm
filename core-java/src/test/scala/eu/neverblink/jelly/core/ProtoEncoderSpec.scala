package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.{JellyConstants, JellyOptions, NamespaceDeclaration, RdfProtoSerializationError}
import eu.neverblink.jelly.core.helpers.*
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*
  import eu.neverblink.jelly.core.ProtoEncoder.Params as Pep

  // Test body
  "a ProtoEncoder" should {
    "encode triple statements" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))
      Triples1.mrl.foreach(triple => encoder.handleTriple(triple.s, triple.p, triple.o))
      assertEncoded(buffer.toSeq, Triples1.encoded(options))
    }

    "encode triple statements with ns decls and an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = true,
        appendableRowBuffer = buffer.asJava
      ))

      for triple <- Triples2NsDecl.mrl do
        triple match
          case t: Triple => encoder.handleTriple(t.s, t.p, t.o)
          case ns: NamespaceDeclaration => encoder.handleNamespace(ns.prefix, Iri(ns.iri))

      assertEncoded(buffer.toSeq, Triples2NsDecl.encoded(options))
    }

    "encode quad statements" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      Quads1.mrl.foreach(quad => encoder.handleQuad(quad.s, quad.p, quad.o, quad.g))
      assertEncoded(buffer.toSeq, Quads1.encoded(options))
    }

    "encode quad statements with an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      for quad <- Quads1.mrl do
        encoder.handleQuad(quad.s, quad.p, quad.o, quad.g)

      assertEncoded(buffer.toSeq, Quads1.encoded(options))
    }

    "encode quad statements (repeated default graph)" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      Quads2RepeatDefault.mrl.foreach(quad => encoder.handleQuad(quad.s, quad.p, quad.o, quad.g))
      assertEncoded(buffer.toSeq, Quads2RepeatDefault.encoded(options))
    }

    "encode graphs with an external buffer" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.GRAPHS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      for (graphName, triples) <- Graphs1.mrl do
        encoder.handleGraphStart(graphName)
        for triple <- triples do
          encoder.handleTriple(triple.s, triple.p, triple.o)
        encoder.handleGraphEnd()

      assertEncoded(buffer.toSeq, Graphs1.encoded(options))
    }

    "not allow to end a graph before starting one" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleGraphEnd()
      }

      error.getMessage should include ("Cannot end a delimited graph before starting one")
    }

    "not allow to use quoted triples as the graph name" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.GRAPHS)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleGraphStart(TripleNode(BlankNode("S"), BlankNode("P"), BlankNode("O")))
      }

      error.getMessage should include ("Cannot encode graph node")
    }

    "not allow to use namespace declarations if they are not enabled" in {
      val buffer = ListBuffer[RdfStreamRow]()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        appendableRowBuffer = buffer.asJava
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleNamespace("test", Iri("http://example.org/test"))
      }

      error.getMessage should include ("Namespace declarations are not enabled in this stream")
    }
  }
