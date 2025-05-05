package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.RdfProtoDeserializationError
import eu.neverblink.jelly.core.helpers.Mrl
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.PatchHandler.*
import eu.neverblink.jelly.core.patch.helpers.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental

@experimental
class PatchDecoderSpec extends AnyWordSpec, Matchers:
  val decoders: Seq[(
    String,
    (AnyPatchHandler[Mrl.Node], RdfPatchOptions) => PatchDecoder,
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
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(
          JellyPatchOptions.SMALL_STRICT.clone
            .setMaxNameTableSize(100_000)
            .setStatementType(stType)
        )
      ))
      val decoder = decFactory(null, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The stream uses a name table size of 100000")
    }

    "throw exception on missing patch stream type" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(
          JellyPatchOptions.SMALL_STRICT.clone
            .setStatementType(stType)
        )
      ))
      val decoder = decFactory(null, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The patch stream type is unspecified")
    }

    for (st, i) <- streamTypes do f"accept stream with type $st" in {
      val opt = JellyPatchOptions.SMALL_STRICT.clone
        .setStatementType(stType)
        .setStreamType(st)

      val input = rdfPatchFrame(Seq(rdfPatchRow(opt)))
      val decoder = decFactory(PatchCollector(), null)
      decoder.ingestFrame(input)
      decoder.getPatchOptions shouldBe opt
    }

    for (st, i) <- streamTypes do f"reject stream with type $st if a different stream type is expected" in {
      val opt = JellyPatchOptions.SMALL_STRICT.clone
        .setStatementType(stType)
        .setStreamType(st)

      val supportedOpt = opt.clone
        .setStreamType(streamTypes((i + 1) % streamTypes.size)._1)

      val input = rdfPatchFrame(Seq(rdfPatchRow(opt)))
      val decoder = decFactory(PatchCollector(), supportedOpt)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("The requested stream type")
      e.getMessage should include("is not supported")
    }

    "handle multiple options rows" in {
      val opt = JellyPatchOptions.SMALL_STRICT.clone
        .setStatementType(stType)
        .setStreamType(PatchStreamType.FLAT)
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(opt),
        rdfPatchRow(opt),
        rdfPatchRow(opt),
      ))
      val decoder = decFactory(PatchCollector(), null)
      decoder.ingestFrame(input)
      decoder.getPatchOptions shouldBe opt
    }

    "decode transactions, punctuations and options" in {
      val inputRows = PatchTestCases.MalformedTransactions.encoded(
        JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(stType)
          .setStreamType(PatchStreamType.PUNCTUATED)

      ) :++ Seq(rdfPatchRow(rdfPatchPunctuation()), rdfPatchRow(rdfPatchPunctuation()))
      val input = rdfPatchFrame(inputRows)
      val out = PatchCollector()
      val decoder = decFactory(out, null)
      decoder.ingestFrame(input)

      out.statements.result() shouldBe PatchTestCases.MalformedTransactions.mrl :++ Seq(
        Mpl.Punctuation,
        Mpl.Punctuation,
      )
    }

    for (st, _) <- streamTypes.take(2) do f"throw exception on punctuations in stream type $st" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(stType)
          .setStreamType(st)
        ),
        rdfPatchRow(rdfPatchPunctuation()),
      ))
      val decoder = decFactory(null, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Unexpected punctuation row in non-punctuated stream")
    }

    "emit punctuation on frame boundaries in stream type FRAME" in {
      val input1 = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(stType)
          .setStreamType(PatchStreamType.FRAME)
        ),
      ))
      val input2 = rdfPatchFrame(Seq())
      val out = PatchCollector()
      val decoder = decFactory(out, null)
      decoder.ingestFrame(input1)
      decoder.ingestFrame(input2)
      decoder.ingestFrame(input2)
      out.statements.result() shouldBe Seq(Mpl.Punctuation, Mpl.Punctuation, Mpl.Punctuation)
    }

    for (st, _) <- streamTypes.filterNot(_._1 == PatchStreamType.FRAME) do
      f"not emit punctuation on frame boundaries in stream type $st" in {
        val input1 = rdfPatchFrame(Seq(
          rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
            .setStatementType(stType)
            .setStreamType(st)
          ),
        ))
        val input2 = rdfPatchFrame(Seq())
        val out = PatchCollector()
        val decoder = decFactory(out, null)
        decoder.ingestFrame(input1)
        decoder.ingestFrame(input2)
        decoder.ingestFrame(input2)
        out.statements.result() shouldBe Seq()
      }

    "throw exception on unknown row type" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(stType)
          .setStreamType(PatchStreamType.FLAT)
        ),
        rdfPatchRow(),
      ))
      val decoder = decFactory(null, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Row kind is not set or unknown: 0")
    }
  }

  "TriplesDecoder" should {
    "decode triples (1)" in {
      val input = PatchTestCases.Triples1.encodedFull(
        JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.TRIPLES)
          .setStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.triplesDecoder(out, null)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Triples1.mrl
    }

    "decode triples (2) with namespace declarations" in {
      val input = PatchTestCases.Triples2NsDecl.encodedFull(
        JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.TRIPLES)
          .setStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.triplesDecoder(out, null)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Triples2NsDecl.mrl
    }

    "ignore graph names in quads" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.TRIPLES)
          .setStreamType(PatchStreamType.FLAT)
        ),
        rdfPatchRowAdd(rdfQuad("b1", "b1", "b1", "b1")),
        rdfPatchRowDelete(rdfQuad("b1", "b1", "b1", "b1")),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.triplesDecoder(out, null)
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
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.QUADS)
          .setStreamType(PatchStreamType.FLAT)
        ),
      ))
      val decoder = MockPatchConverterFactory.triplesDecoder(null, null)
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
        JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.QUADS)
          .setStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.quadsDecoder(out, null)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Quads1.mrl
    }

    "not accept a stream with statement type TRIPLES" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.TRIPLES)
          .setStreamType(PatchStreamType.FLAT)
        ),
      ))
      val decoder = MockPatchConverterFactory.quadsDecoder(null, null)
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
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      decoder.getPatchOptions shouldBe null
    }

    "throw exception if the first row in the stream is not options (add)" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRowAdd(rdfQuad("", "", "", "")),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Statement type is not set, statement add command cannot be decoded")
    }

    "throw exception if the first row in the stream is not options (delete)" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRowDelete(rdfQuad("", "", "", "")),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Statement type is not set, statement delete command cannot be decoded")
    }

    "ignore multiple options rows" in {
      val opt = JellyPatchOptions.SMALL_STRICT.clone
        .setStatementType(PatchStatementType.TRIPLES)
        .setStreamType(PatchStreamType.FLAT)
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(opt),
        rdfPatchRow(opt),
        rdfPatchRow(opt),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      decoder.ingestFrame(input)
      decoder.getPatchOptions shouldBe opt
    }

    "decode quads" in {
      val input = PatchTestCases.Quads1.encodedFull(
        JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.QUADS)
          .setStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Quads1.mrl
    }

    "decode triples" in {
      val input = PatchTestCases.Triples1.encodedFull(
        JellyPatchOptions.SMALL_STRICT.clone
          .setStatementType(PatchStatementType.TRIPLES)
          .setStreamType(PatchStreamType.FLAT),
        10_000
      ).head
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      decoder.ingestFrame(input)
      out.statements.result() shouldBe PatchTestCases.Triples1.mrl
    }

    "throw exception if the statement type is not set" in {
      val input = rdfPatchFrame(Seq(
        rdfPatchRow(JellyPatchOptions.SMALL_STRICT.clone
          .setStreamType(PatchStreamType.FLAT)
        ),
      ))
      val out = PatchCollector()
      val decoder = MockPatchConverterFactory.anyStatementDecoder(out, null)
      val e = intercept[RdfProtoDeserializationError] {
        decoder.ingestFrame(input)
      }
      e.getMessage should include("Incoming stream has no statement type set")
    }
  }
