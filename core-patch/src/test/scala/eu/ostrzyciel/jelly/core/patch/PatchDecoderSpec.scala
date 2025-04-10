package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoDeserializationError
import eu.ostrzyciel.jelly.core.helpers.Mrl
import eu.ostrzyciel.jelly.core.patch.handler.*
import eu.ostrzyciel.jelly.core.patch.helpers.{MockPatchConverterFactory, Mpl, PatchCollector, PatchTestCases}
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
  }
