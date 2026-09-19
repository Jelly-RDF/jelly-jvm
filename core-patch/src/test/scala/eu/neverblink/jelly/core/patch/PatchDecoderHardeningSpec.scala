package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl.Node
import eu.neverblink.jelly.core.helpers.{ByteFuzzer}
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.PatchHandler.AnyPatchHandler
import eu.neverblink.jelly.core.patch.helpers.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*
import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.IOException
import scala.annotation.experimental

/** Tests for decoding hostile input: rows that parse as valid protobuf, but whose contents are
  * chosen to make the decoder dereference or index something it never checked.
  *
  * Everything a decoder rejects must be rejected as [[RdfProtoDeserializationError]].
  */
@experimental
class PatchDecoderHardeningSpec extends AnyWordSpec, Matchers:

  private val smallDtTableSize = JellyPatchOptions.SMALL_STRICT.getMaxDatatypeTableSize

  private def options(datatypeTableSize: Int = smallDtTableSize): RdfPatchOptions =
    JellyPatchOptions.SMALL_STRICT.clone
      .setStatementType(PatchStatementType.TRIPLES)
      .setStreamType(PatchStreamType.FLAT)
      .setMaxDatatypeTableSize(datatypeTableSize)

  private def expectRejected(rows: Seq[RdfPatchRow]): RdfProtoDeserializationError =
    val decoder = MockPatchConverterFactory.anyStatementDecoder(PatchCollector(), null)
    val outcome =
      try Right(decoder.ingestFrame(rdfPatchFrame(rows)))
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
          rdfPatchRow(options()),
          rdfPatchRow(rdfDatatypeEntry(smallDtTableSize + 1, "https://test.org/int")),
        ),
      )
    }

    "reject an entry with an id that does not fit in a signed int" in {
      // uint32 ids above 2^31 - 1 come back as negative ints
      expectRejected(
        Seq(rdfPatchRow(options()), rdfPatchRow(rdfDatatypeEntry(-1, "https://test.org/int"))),
      )
    }

    "reject an entry when the stream declared no datatype table" in {
      expectRejected(
        Seq(
          rdfPatchRow(options(datatypeTableSize = 0)),
          rdfPatchRow(rdfDatatypeEntry(1, "https://test.org/int")),
        ),
      )
    }

    "reject a run of implicitly numbered entries that runs off the end of the table" in {
      // Id 0 means "the one after the last", so a long enough run walks past the last slot
      val entries = (0 to smallDtTableSize)
        .map(i => rdfPatchRow(rdfDatatypeEntry(0, s"https://test.org/dt$i")))
      expectRejected(rdfPatchRow(options()) +: entries)
    }
  }

  "a namespace add row" should {
    "be rejected when it has no IRI" in {
      expectRejected(
        Seq(rdfPatchRow(options()), rdfPatchRowAdd(rdfPatchNamespace("ex"))),
      )
    }

    "be rejected when its IRI refers to a name slot that was never set" in {
      expectRejected(
        Seq(
          rdfPatchRow(options()),
          rdfPatchRow(rdfPrefixEntry(1, "https://test.org/")),
          rdfPatchRowAdd(rdfPatchNamespace("ex", rdfIri(1, 1))),
        ),
      )
    }

    "be rejected when its IRI refers to a prefix slot that was never set" in {
      expectRejected(
        Seq(
          rdfPatchRow(options()),
          rdfPatchRow(rdfNameEntry(1, "x")),
          rdfPatchRowAdd(rdfPatchNamespace("ex", rdfIri(1, 1))),
        ),
      )
    }
  }

  "a namespace delete row" should {
    "be rejected when its IRI refers to a name slot that was never set" in {
      expectRejected(
        Seq(
          rdfPatchRow(options()),
          rdfPatchRow(rdfPrefixEntry(1, "https://test.org/")),
          rdfPatchRowDelete(rdfPatchNamespace("ex", rdfIri(1, 1))),
        ),
      )
    }
  }

  "a statement row" should {
    "be rejected when its literal refers to a datatype slot that was never set" in {
      expectRejected(
        Seq(
          rdfPatchRow(options()),
          rdfPatchRow(rdfNameEntry(1, "https://test.org/s")),
          rdfPatchRowAdd(
            rdfQuad(rdfIri(0, 1), rdfIri(0, 1), rdfLiteral("1", 1)),
          ),
        ),
      )
    }
  }

  "ingestRow" should {
    "reject a null row instead of dereferencing it" in {
      val decoder = MockPatchConverterFactory.anyStatementDecoder(PatchCollector(), null)
      decoder.ingestRow(rdfPatchRow(options()))
      val outcome =
        try Right(decoder.ingestRow(null))
        catch case t: Throwable => Left(t)
      outcome match
        case Left(_: RdfProtoDeserializationError) => succeed
        case Left(other) =>
          fail(s"Expected the row to be rejected with RdfProtoDeserializationError, got $other")
        case Right(_) => fail("Expected the row to be rejected, but the decoder accepted it.")
    }
  }

  "the decoder" should {
    "handle mutated frames (fuzzing)" in {
      var reachedDecoder = 0
      val findings = ByteFuzzer.findings(corpus, fuzzIterations, fuzzSeed, isExpected) { bytes =>
        val frame = RdfPatchFrame.parseFrom(bytes)
        reachedDecoder += 1
        MockPatchConverterFactory.anyStatementDecoder(NoOpHandler, null).ingestFrame(frame)
      }
      withClue(s"${findings.size} kinds of unchecked failure:\n${findings.mkString("\n")}\n") {
        findings shouldBe empty
      }
      withClue("mutations that got past the parser: ") {
        reachedDecoder should be > fuzzIterations / 20
      }
    }
  }

  private lazy val fuzzIterations =
    sys.env.get("JELLY_FUZZ_ITERATIONS").map(_.toInt).getOrElse(30_000)
  private lazy val fuzzSeed = sys.env.get("JELLY_FUZZ_SEED").map(_.toLong).getOrElse(20260918L)

  private def isExpected(t: Throwable): Boolean = t match
    case _: RdfProtoDeserializationError => true
    case _: IOException => true
    case _ => false

  /** Valid frames spanning the patch row kinds, as the seed examples. */
  private lazy val corpus: Seq[Array[Byte]] =
    val frames = Seq(
      rdfPatchFrame(
        Seq(
          rdfPatchRow(options()),
          rdfPatchRow(rdfPrefixEntry(1, "https://test.org/")),
          rdfPatchRow(rdfNameEntry(1, "a")),
          rdfPatchRow(rdfDatatypeEntry(1, "https://test.org/int")),
          rdfPatchRowAdd(rdfQuad(rdfIri(1, 1), rdfIri(1, 1), rdfLiteral("1", 1))),
          rdfPatchRowDelete(rdfQuad(rdfIri(1, 1), rdfIri(1, 1), rdfLiteral("x", "en"))),
          rdfPatchRowAdd(rdfPatchNamespace("ex", rdfIri(1, 1))),
          rdfPatchRowDelete(rdfPatchNamespace("ex", rdfIri(1, 1))),
          rdfPatchRow(rdfPatchHeader("k", rdfIri(1, 1))),
          rdfPatchRow(rdfPatchTransactionStart()),
          rdfPatchRow(rdfPatchTransactionCommit()),
        ),
      ),
      rdfPatchFrame(
        Seq(
          rdfPatchRow(
            JellyPatchOptions.SMALL_STRICT.clone
              .setStatementType(PatchStatementType.QUADS)
              .setStreamType(PatchStreamType.PUNCTUATED),
          ),
          rdfPatchRow(rdfNameEntry(1, "https://test.org/s")),
          rdfPatchRowAdd(rdfQuad(rdfIri(0, 1), rdfIri(0, 1), "bnode", rdfDefaultGraph())),
          rdfPatchRowAdd(
            rdfQuad(rdfTriple(rdfIri(0, 1), rdfIri(0, 1), "b"), rdfIri(0, 1), "o", "g"),
          ),
          rdfPatchRow(rdfPatchPunctuation()),
          rdfPatchRow(rdfPatchTransactionAbort()),
        ),
      ),
    )
    frames.map(_.toByteArray)

  /** Keeps the handler's own behaviour out of the fuzzer's findings. */
  private object NoOpHandler extends AnyPatchHandler[Node]:
    override def addQuad(s: Node, p: Node, o: Node, g: Node): Unit = ()
    override def deleteQuad(s: Node, p: Node, o: Node, g: Node): Unit = ()
    override def addTriple(s: Node, p: Node, o: Node): Unit = ()
    override def deleteTriple(s: Node, p: Node, o: Node): Unit = ()
    override def transactionStart(): Unit = ()
    override def transactionCommit(): Unit = ()
    override def transactionAbort(): Unit = ()
    override def addNamespace(name: String, iriValue: Node, graph: Node): Unit = ()
    override def deleteNamespace(name: String, iriValue: Node, graph: Node): Unit = ()
    override def header(key: String, value: Node): Unit = ()
    override def punctuation(): Unit = ()
