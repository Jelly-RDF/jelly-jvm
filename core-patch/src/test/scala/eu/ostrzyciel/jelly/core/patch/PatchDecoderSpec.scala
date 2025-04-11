package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoDeserializationError
import eu.ostrzyciel.jelly.core.helpers.Mrl
import eu.ostrzyciel.jelly.core.patch.handler.*
import eu.ostrzyciel.jelly.core.patch.helpers.*
import eu.ostrzyciel.jelly.core.proto.v1.*
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental

@experimental
class PatchDecoderSpec extends AnyWordSpec, Matchers:
  val decoders: Seq[(
    String,
    (AnyPatchHandler[Mrl.Node], Option[RdfPatchOptions]) => PatchDecoder,
    PatchStatementType
  )] = Seq(
    ("TriplesDecoder", MockPatchConverterFactory.triplesDecoder, PatchStatementType.TRIPLES),
    ("QuadsDecoder", MockPatchConverterFactory.quadsDecoder, PatchStatementType.QUADS),
    ("AnyDecoder", MockPatchConverterFactory.anyStatementDecoder, PatchStatementType.TRIPLES),
  )

  val streamTypes: Seq[(PatchStreamType, Int)] = Seq(
    PatchStreamType.FLAT,
    PatchStreamType.FRAME,
    PatchStreamType.PUNCTUATED
  ).zipWithIndex

  for (decName, decFactory, stType) <- decoders do decName should {
    "throw exception on overly large name lookup table" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withMaxNameTableSize(100_000)
          .withStatementType(stType)
        )
      ))
      val decoder = decFactory(null, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The stream uses a name table size of 100000")
    }

    "throw exception on missing patch stream type" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(stType)
        )
      ))
      val decoder = decFactory(null, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The patch stream type is unspecified")
    }

    for (st, i) <- streamTypes do f"accept stream with type $st" in {
      val opt = JellyPatchOptions.smallStrict
        .withStatementType(stType)
        .withStreamType(st)
      val input = RdfPatchFrame(Seq(RdfPatchRow.ofOptions(opt)))
      val decoder = decFactory(PatchCollector(), None)
      decoder.ingestFrame(input)
      decoder.getPatchOpt shouldBe Some(opt)
    }

    for (st, i) <- streamTypes do f"reject stream with type $st if a different stream type is expected" in {
      val opt = JellyPatchOptions.smallStrict
        .withStatementType(stType)
        .withStreamType(st)
      val supportedOpt = opt.withStreamType(streamTypes((i + 1) % streamTypes.size)._1)
      val input = RdfPatchFrame(Seq(RdfPatchRow.ofOptions(opt)))
      val decoder = decFactory(PatchCollector(), Some(supportedOpt))
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The requested stream type")
      e.getMessage should include("is not supported")
    }

    "handle multiple options rows" in {
      val opt = JellyPatchOptions.smallStrict
        .withStatementType(stType)
        .withStreamType(PatchStreamType.FLAT)
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(opt),
        RdfPatchRow.ofOptions(opt),
        RdfPatchRow.ofOptions(opt),
      ))
      val decoder = decFactory(PatchCollector(), None)
      decoder.ingestFrame(input)
      decoder.getPatchOpt shouldBe Some(opt)
    }

    "decode transactions, punctuations and options" in {
      val inputRows = PatchTestCases.MalformedTransactions.encoded(
        JellyPatchOptions.smallStrict
          .withStatementType(stType)
          .withStreamType(PatchStreamType.PUNCTUATED)
      ) :++ Seq(RdfPatchRow.ofPunctuation, RdfPatchRow.ofPunctuation)
      val input = RdfPatchFrame(inputRows)
      val out = PatchCollector()
      val decoder = decFactory(out, None)
      decoder.ingestFrame(input)

      out.statements.result() shouldBe PatchTestCases.MalformedTransactions.mrl :++ Seq(
        Mpl.Punctuation,
        Mpl.Punctuation,
      )
    }

    for (st, _) <- streamTypes.take(2) do f"throw exception on punctuations in stream type $st" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(stType)
          .withStreamType(st)
        ),
        RdfPatchRow.ofPunctuation,
      ))
      val decoder = decFactory(null, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Unexpected punctuation row in non-punctuated stream")
    }

    "emit punctuation on frame boundaries in stream type FRAME" in {
      val input1 = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(stType)
          .withStreamType(PatchStreamType.FRAME)
        ),
      ))
      val input2 = RdfPatchFrame(Seq())
      val out = PatchCollector()
      val decoder = decFactory(out, None)
      decoder.ingestFrame(input1)
      decoder.ingestFrame(input2)
      decoder.ingestFrame(input2)
      out.statements.result() shouldBe Seq(Mpl.Punctuation, Mpl.Punctuation, Mpl.Punctuation)
    }

    for (st, _) <- streamTypes.filterNot(_._1.isFrame) do
      f"not emit punctuation on frame boundaries in stream type $st" in {
        val input1 = RdfPatchFrame(Seq(
          RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
            .withStatementType(stType)
            .withStreamType(st)
          ),
        ))
        val input2 = RdfPatchFrame(Seq())
        val out = PatchCollector()
        val decoder = decFactory(out, None)
        decoder.ingestFrame(input1)
        decoder.ingestFrame(input2)
        decoder.ingestFrame(input2)
        out.statements.result() shouldBe Seq()
      }

    "throw exception on unknown row type" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(stType)
          .withStreamType(PatchStreamType.FLAT)
        ),
        RdfPatchRow(null, 100),
      ))
      val decoder = decFactory(null, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Row kind is not set or unknown: 100")
    }
  }

  "TriplesDecoder" should {
    "decode triples (1)" in {
      val input = PatchTestCases.Triples1.encodedFull(
        JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.TRIPLES)
          .withStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.triplesDecoder(out, None)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Triples1.mrl
    }

    "decode triples (2) with namespace declarations" in {
      val input = PatchTestCases.Triples2NsDecl.encodedFull(
        JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.TRIPLES)
          .withStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.triplesDecoder(out, None)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Triples2NsDecl.mrl
    }

    "ignore graph names in quads" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.TRIPLES)
          .withStreamType(PatchStreamType.FLAT)
        ),
        RdfPatchRow.ofStatementAdd(RdfQuad(
          RdfTerm.Bnode("b1"), RdfTerm.Bnode("b1"), RdfTerm.Bnode("b1"), RdfTerm.Bnode("b1")
        )),
        RdfPatchRow.ofStatementDelete(RdfQuad(
          RdfTerm.Bnode("b1"), RdfTerm.Bnode("b1"), RdfTerm.Bnode("b1"), RdfTerm.Bnode("b1")
        )),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.triplesDecoder(out, None)
      decoder.ingestFrame(input)
      out.statements.size should be (2)
      out.statements should be (Seq(
        Mpl.Add(Mrl.Triple(
          Mrl.BlankNode("b1"), Mrl.BlankNode("b1"), Mrl.BlankNode("b1")
        )),
        Mpl.Delete(Mrl.Triple(
          Mrl.BlankNode("b1"), Mrl.BlankNode("b1"), Mrl.BlankNode("b1")
        )),
      ))
    }

    "not accept a stream with statement type QUADS" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.QUADS)
          .withStreamType(PatchStreamType.FLAT)
        ),
      ))
      val decoder = MockPatchConverterFactory.triplesDecoder(null, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Incoming stream with statement type")
      e.getMessage should include("cannot be decoded by this decoder")
    }
  }

  "QuadsDecoder" should {
    "decode quads (1)" in {
      val input = PatchTestCases.Quads1.encodedFull(
        JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.QUADS)
          .withStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.quadsDecoder(out, None)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Quads1.mrl
    }

    "not accept a stream with statement type TRIPLES" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.TRIPLES)
          .withStreamType(PatchStreamType.FLAT)
        ),
      ))
      val decoder = MockPatchConverterFactory.quadsDecoder(null, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Incoming stream with statement type")
      e.getMessage should include("cannot be decoded by this decoder")
    }
  }

  "AnyStatementDecoder" should {
    "return no options if the inner decoder is not initialized" in {
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, None)
      decoder.getPatchOpt shouldBe None
    }

    "throw exception if the first row in the stream is not options" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofStatementAdd(RdfQuad()),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The first row in the stream must be an RdfPatchOptions row")
    }

    "ignore multiple options rows" in {
      val opt = JellyPatchOptions.smallStrict
        .withStatementType(PatchStatementType.TRIPLES)
        .withStreamType(PatchStreamType.FLAT)
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(opt),
        RdfPatchRow.ofOptions(opt),
        RdfPatchRow.ofOptions(opt),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, None)
      decoder.ingestFrame(input)
      decoder.getPatchOpt shouldBe Some(opt)
    }

    "decode quads" in {
      val input = PatchTestCases.Quads1.encodedFull(
        JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.QUADS)
          .withStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, None)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Quads1.mrl
    }

    "throw exception if the statement type is not set" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStreamType(PatchStreamType.FLAT)
        ),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Incoming stream has no statement type set")
    }

    "throw exception if the statement type is not supported" in {
      val input = RdfPatchFrame(Seq(
        RdfPatchRow.ofOptions(JellyPatchOptions.smallStrict
          .withStatementType(PatchStatementType.Unrecognized(100))
          .withStreamType(PatchStreamType.FLAT)
        ),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, None)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Incoming stream with statement type UNRECOGNIZED cannot be decoded by this decoder")
    }
  }
