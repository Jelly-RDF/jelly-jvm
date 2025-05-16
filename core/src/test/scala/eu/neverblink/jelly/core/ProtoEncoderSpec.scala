package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.helpers.*
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.memory.{EncoderAllocator, RowBuffer}
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*
  import eu.neverblink.jelly.core.ProtoEncoder.Params as Pep

  // Test body
  "a ProtoEncoder" should {
    "encode triple statements" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))
      Triples1.mrl.foreach(triple => encoder.handleTriple(triple.s, triple.p, triple.o))
      assertEncoded(buffer.getRows.asScala.toSeq, Triples1.encoded(options))
    }

    "encode triple statements with ns decls and an external buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = true,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      for triple <- Triples2NsDecl.mrl do
        triple match
          case t: Triple => encoder.handleTriple(t.s, t.p, t.o)
          case ns: NamespaceDeclaration => encoder.handleNamespace(ns.prefix, Iri(ns.iri))

      assertEncoded(buffer.getRows.asScala.toSeq, Triples2NsDecl.encoded(options))
    }

    "encode quad statements" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      Quads1.mrl.foreach(quad => encoder.handleQuad(quad.s, quad.p, quad.o, quad.g))
      assertEncoded(buffer.getRows.asScala.toSeq, Quads1.encoded(options))
    }

    "encode quad statements with an external buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      for quad <- Quads1.mrl do
        encoder.handleQuad(quad.s, quad.p, quad.o, quad.g)

      assertEncoded(buffer.getRows.asScala.toSeq, Quads1.encoded(options))
    }

    "encode quad statements (repeated default graph)" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      Quads2RepeatDefault.mrl.foreach(quad => encoder.handleQuad(quad.s, quad.p, quad.o, quad.g))
      assertEncoded(buffer.getRows.asScala.toSeq, Quads2RepeatDefault.encoded(options))
    }

    "encode graphs with an external buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.GRAPHS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      for (graphName, triples) <- Graphs1.mrl do
        encoder.handleGraphStart(graphName)
        for triple <- triples do
          encoder.handleTriple(triple.s, triple.p, triple.o)
        encoder.handleGraphEnd()

      assertEncoded(buffer.getRows.asScala.toSeq, Graphs1.encoded(options))
    }

    "not allow to end a graph before starting one" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleGraphEnd()
      }

      error.getMessage should include ("Cannot end a delimited graph before starting one")
    }

    "not allow to use quoted triples as the graph name" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.GRAPHS)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleGraphStart(TripleNode(BlankNode("S"), BlankNode("P"), BlankNode("O")))
      }

      error.getMessage should include ("Cannot encode graph node")
    }

    "not allow to use namespace declarations if they are not enabled" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)

      val encoder = MockConverterFactory.encoder(Pep(
        options,
        enableNamespaceDeclarations = false,
        rowBuffer = buffer,
        allocator = EncoderAllocator.newHeapAllocator(),
      ))

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleNamespace("test", Iri("http://example.org/test"))
      }

      error.getMessage should include ("Namespace declarations are not enabled in this stream")
    }
  }
