package eu.neverblink.jelly.core

import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.helpers.{MockConverterFactory, ProtoCollector}
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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

  /** Feeds the rows to a fresh triples decoder and hands back whatever the last one threw. */
  private def expectRejected(rows: Seq[RdfStreamRowValue]): RdfProtoDeserializationError =
    val decoder = MockConverterFactory.triplesDecoder(
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
