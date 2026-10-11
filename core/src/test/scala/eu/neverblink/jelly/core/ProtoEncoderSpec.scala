package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.helpers.*
import eu.neverblink.jelly.core.helpers.Assertions.*
import eu.neverblink.jelly.core.helpers.Mrl.*
import eu.neverblink.jelly.core.memory.{EncoderAllocator, RowBuffer}
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*
import scala.annotation.nowarn

// Covers the deprecated Jelly-RDF 1.1 (row layout) encoder
@nowarn("cat=deprecation")
class ProtoEncoderSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*
  import eu.neverblink.jelly.core.ProtoEncoder.Params as Pep

  // Test body
  "a ProtoEncoder" should {
    val tripleBasicCases = Seq(
      ("Triples1", Triples1),
      ("Triples3LongStrings", Triples3LongStrings),
    )

    for (name, testCase) <- tripleBasicCases do
      s"encode triple statements ($name)" in {
        val buffer = RowBuffer.newLazyImmutable()
        val options = JellyOptions.SMALL_GENERALIZED.clone
          .setPhysicalType(PhysicalStreamType.TRIPLES)
          .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

        val encoder = MockConverterFactory.encoder(
          Pep(
            options,
            enableNamespaceDeclarations = false,
            rowBuffer = buffer,
            allocator = EncoderAllocator.newHeapAllocator(),
          ),
        )

        testCase.mrl.foreach(triple => encoder.handleTriple(triple.s, triple.p, triple.o))

        val observed = buffer.getRows.asScala.toSeq
        assertEncoded(observed, testCase.encoded(options))
        assertSizesPrecomputed(observed)
      }

    "encode triple statements with ns decls and an external buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setVersion(JellyConstants.PROTO_VERSION_1_1_X)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = true,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      for triple <- Triples2NsDecl.mrl do
        triple match
          case t: Triple => encoder.handleTriple(t.s, t.p, t.o)
          case ns: NamespaceDeclaration => encoder.handleNamespace(ns.prefix, Iri(ns.iri))

      val observed = buffer.getRows.asScala.toSeq
      assertEncoded(observed, Triples2NsDecl.encoded(options))
      assertSizesPrecomputed(observed)
    }

    "encode quad statements" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      Quads1.mrl.foreach(quad => encoder.handleQuad(quad.s, quad.p, quad.o, quad.g))

      val observed = buffer.getRows.asScala.toSeq
      assertEncoded(observed, Quads1.encoded(options))
      assertSizesPrecomputed(observed)
    }

    "encode quad statements with an external buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      for quad <- Quads1.mrl do encoder.handleQuad(quad.s, quad.p, quad.o, quad.g)

      val observed = buffer.getRows.asScala.toSeq
      assertEncoded(observed, Quads1.encoded(options))
      assertSizesPrecomputed(observed)
    }

    "encode quad statements (repeated default graph)" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      Quads2RepeatDefault.mrl.foreach(quad => encoder.handleQuad(quad.s, quad.p, quad.o, quad.g))

      val observed = buffer.getRows.asScala.toSeq
      assertEncoded(observed, Quads2RepeatDefault.encoded(options))
      assertSizesPrecomputed(observed)
    }

    "encode graphs with an external buffer" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.GRAPHS)
        .setVersion(JellyConstants.PROTO_VERSION_1_0_X)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      for (graphName, triples) <- Graphs1.mrl do
        encoder.handleGraphStart(graphName)
        for triple <- triples do encoder.handleTriple(triple.s, triple.p, triple.o)
        encoder.handleGraphEnd()

      val observed = buffer.getRows.asScala.toSeq
      assertEncoded(observed, Graphs1.encoded(options))
      assertSizesPrecomputed(observed)
    }

    "not allow to end a graph before starting one" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.QUADS)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleGraphEnd()
      }

      error.getMessage should include("Cannot end a delimited graph before starting one")
    }

    "not allow to use quoted triples as the graph name" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.GRAPHS)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleGraphStart(TripleNode(BlankNode("S"), BlankNode("P"), BlankNode("O")))
      }

      error.getMessage should include("Cannot encode graph node")
    }

    "not allow to use namespace declarations if they are not enabled" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_GENERALIZED.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)

      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )

      val error = intercept[RdfProtoSerializationError] {
        encoder.handleNamespace("test", Iri("http://example.org/test"))
      }

      error.getMessage should include("Namespace declarations are not enabled in this stream")
    }

    // The lookups must not evict an entry that the statement being encoded refers to, even when
    // the statement has used the table so many times since that the entry is no longer recent.
    "encode a statement that uses the whole datatype table" in {
      val buffer = RowBuffer.newLazyImmutable()
      val options = JellyOptions.SMALL_ALL_FEATURES.clone
        .setPhysicalType(PhysicalStreamType.TRIPLES)
        .setMaxDatatypeTableSize(8)
      val encoder = MockConverterFactory.encoder(
        Pep(
          options,
          enableNamespaceDeclarations = false,
          rowBuffer = buffer,
          allocator = EncoderAllocator.newHeapAllocator(),
        ),
      )
      val p = Iri("https://example.org/p")
      def literal(dt: Int) = DtLiteral(s"v$dt", Datatype(s"https://example.org/dt$dt"))
      // Quoted triples nested in the subject, so that the literals are encoded in this order
      def statement(dts: Seq[Int]) = Triple(
        dts.foldLeft[Node](Iri("https://example.org/s"))((s, dt) => TripleNode(s, p, literal(dt))),
        p,
        Iri("https://example.org/o"),
      )
      val statements = Seq(
        // Fills the table
        statement(1 to 8),
        // Uses datatype 1 again, then so many new ones that the eviction order comes back to it
        statement(1 +: (9 to 15)),
      )
      statements.foreach(t => encoder.handleTriple(t.s, t.p, t.o))

      val collector = ProtoCollector()
      val decoder = MockConverterFactory.triplesDecoder(
        collector,
        JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
      )
      buffer.getRows.asScala.foreach(decoder.ingestRow)
      collector.statements.toSeq should be(statements)
    }
  }
