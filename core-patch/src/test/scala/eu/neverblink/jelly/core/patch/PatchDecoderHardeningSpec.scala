package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.helpers.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*
import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
