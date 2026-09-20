package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.RdfHandler.AnyRdfHandler
import eu.neverblink.jelly.core.helpers.Mrl.{Datatype, Node}
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.helpers.*
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.IOException

/** Tests for decoding hostile input: rows that parse as valid protobuf, but whose contents are
  * chosen to make the decoder dereference or index something it never checked.
  *
  * Everything a decoder rejects must be rejected as [[RdfProtoDeserializationError]].
  */
class ProtoDecoderHardeningSpec extends AnyWordSpec, Matchers:
  import ProtoTestCases.*

  private val smallDtTableSize = JellyOptions.SMALL_GENERALIZED.getMaxDatatypeTableSize

  private def options(datatypeTableSize: Int = smallDtTableSize): RdfStreamOptions =
    JellyOptions.SMALL_GENERALIZED.clone
      .setPhysicalType(PhysicalStreamType.TRIPLES)
      .setMaxDatatypeTableSize(datatypeTableSize)

  private type Factory =
    JellyConverterFactory[Node, Datatype, MockProtoEncoderConverter, MockProtoDecoderConverter]

  /** Feeds the rows to a fresh triples decoder and hands back whatever the last one threw. */
  private def expectRejected(
      rows: Seq[RdfStreamRowValue],
      factory: Factory = MockConverterFactory,
  ): RdfProtoDeserializationError =
    val decoder = factory.triplesDecoder(
      ProtoCollector(),
      JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    )
    val encoded = wrapEncoded(rows)
    val outcome =
      try Right(encoded.foreach(decoder.ingestRow))
      catch case t: Throwable => Left(t)
    outcome match
      case Left(e: RdfProtoDeserializationError) => e
      case Left(other) =>
        fail(s"Expected the row to be rejected with RdfProtoDeserializationError, got $other")
      case Right(_) =>
        fail("Expected the row to be rejected, but the decoder accepted it.")

  "the datatype lookup" should {
    "reject an entry past the end of the table" in {
      expectRejected(
        Seq(
          options(),
          rdfDatatypeEntry(smallDtTableSize + 1, "https://test.org/int"),
        ),
      )
    }

    "reject an entry with an id that does not fit in a signed int" in {
      // uint32 ids above 2^31 - 1 come back as negative ints
      expectRejected(Seq(options(), rdfDatatypeEntry(-1, "https://test.org/int")))
    }

    "reject an entry when the stream declared no datatype table" in {
      expectRejected(
        Seq(options(datatypeTableSize = 0), rdfDatatypeEntry(1, "https://test.org/int")),
      )
    }

    "reject a run of implicitly numbered entries that runs off the end of the table" in {
      val entries = (0 to smallDtTableSize)
        .map(i => rdfDatatypeEntry(0, s"https://test.org/dt$i"))
      expectRejected(options() +: entries)
    }

    "reject a literal referring to a datatype slot that was never set" in {
      expectRejected(
        Seq(
          options(),
          rdfNameEntry(1, "https://test.org/s"),
          rdfTriple(rdfIri(0, 1), rdfIri(0, 1), rdfLiteral("1", 1)),
        ),
      )
    }
  }

  "a namespace declaration row" should {
    "be rejected when it carries no IRI at all" in {
      expectRejected(
        Seq(options(), RdfNamespaceDeclaration.newInstance().setName("ex")),
      )
    }

    "be rejected when its IRI refers to a name slot that was never set" in {
      expectRejected(
        Seq(
          options(),
          rdfPrefixEntry(1, "https://test.org/"),
          rdfNamespaceDeclaration("ex", rdfIri(1, 1)),
        ),
      )
    }

    "be rejected when its IRI refers to a prefix slot that was never set" in {
      expectRejected(
        Seq(
          options(),
          rdfNameEntry(1, "x"),
          rdfNamespaceDeclaration("ex", rdfIri(1, 1)),
        ),
      )
    }
  }

  "an IRI refused by the RDF library" should {
    "be rejected in a triple" in {
      expectRejected(
        Seq(
          options(),
          rdfNameEntry(1, "relative/subject"),
          rdfTriple(rdfIri(0, 1), rdfIri(0, 1), rdfIri(0, 1)),
        ),
        StrictMockConverterFactory,
      ).getMessage should include("relative/subject")
    }

    "be rejected in a namespace declaration" in {
      expectRejected(
        Seq(
          options(),
          rdfNameEntry(1, "relative/ns"),
          rdfNamespaceDeclaration("ex", rdfIri(0, 1)),
        ),
        StrictMockConverterFactory,
      ).getMessage should include("namespace declaration 'ex'")
    }

    "be rejected in a datatype lookup entry" in {
      expectRejected(
        Seq(options(), rdfDatatypeEntry(1, "relative/dt")),
        StrictMockConverterFactory,
      ).getMessage should include("datatype 'relative/dt'")
    }
  }

  "the decoder" should {
    "handle mutated frames (fuzzing)" in {
      var reachedDecoder = 0
      val findings = ByteFuzzer.findings(corpus, fuzzIterations, fuzzSeed, isExpected) { bytes =>
        val frame = RdfStreamFrame.parseFrom(bytes)
        reachedDecoder += 1
        val decoder = MockConverterFactory.anyStatementDecoder(
          NoOpHandler,
          JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
        )
        frame.getRows.forEach(decoder.ingestRow)
      }
      withClue(s"${findings.size} kinds of unchecked failure:\n${findings.mkString("\n")}\n") {
        findings shouldBe empty
      }
      withClue("mutations that got past the parser: ") {
        reachedDecoder should be > fuzzIterations / 20
      }
    }
  }

  // Bump the iterations for a longer soak run, following the convention of SparqlFuzzSpec
  private lazy val fuzzIterations =
    sys.env.get("JELLY_FUZZ_ITERATIONS").map(_.toInt).getOrElse(30_000)
  private lazy val fuzzSeed = sys.env.get("JELLY_FUZZ_SEED").map(_.toLong).getOrElse(20260918L)

  private def isExpected(t: Throwable): Boolean = t match
    case _: RdfProtoDeserializationError => true
    case _: IOException => true
    case _ => false

  private lazy val corpus: Seq[Array[Byte]] =
    def opt(physicalType: PhysicalStreamType) =
      JellyOptions.SMALL_ALL_FEATURES.clone.setPhysicalType(physicalType)
    val frames =
      Triples1.encodedFull(opt(PhysicalStreamType.TRIPLES), 4) ++
        Triples2NsDecl.encodedFull(opt(PhysicalStreamType.TRIPLES), 4) ++
        Quads1.encodedFull(opt(PhysicalStreamType.QUADS), 4) ++
        Graphs1.encodedFull(opt(PhysicalStreamType.GRAPHS), 4)
    frames.map(_.toByteArray)

  /** Keeps the handler's own behaviour out of the fuzzer's findings. */
  private object NoOpHandler extends AnyRdfHandler[Node]:
    override def handleNamespace(prefix: String, namespace: Node): Unit = ()
    override def handleTriple(s: Node, p: Node, o: Node): Unit = ()
    override def handleQuad(s: Node, p: Node, o: Node, g: Node): Unit = ()
    override def handleGraphStart(graph: Node): Unit = ()
    override def handleGraphEnd(): Unit = ()
