package eu.ostrzyciel.jelly.core

import com.google.protobuf.ByteString
import eu.ostrzyciel.jelly.core.ProtoTestCases.*
import eu.ostrzyciel.jelly.core.helpers.{MockConverterFactory, Mrl}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Random

/**
 * Unit tests for the ProtoTranscoder class.
 * See also integration tests: [[eu.ostrzyciel.jelly.integration_tests.CrossTranscodingSpec]]
 */
class ProtoTranscoderSpec extends AnyWordSpec, Inspectors, Matchers:
  def smallOptions(prefixTableSize: Int) = RdfStreamOptions(
    maxNameTableSize = 4,
    maxPrefixTableSize = prefixTableSize,
    maxDatatypeTableSize = 8,
  )

  val testCases: Seq[(String, PhysicalStreamType,
    TestCase[Mrl.Triple | Mrl.Quad | (Mrl.Node, Iterable[Mrl.Triple]) | NamespaceDeclaration]
  )] = Seq(
    ("Triples1", PhysicalStreamType.TRIPLES, Triples1),
    ("Triples2NsDecl", PhysicalStreamType.TRIPLES, Triples2NsDecl),
    ("Quads1", PhysicalStreamType.QUADS, Quads1),
    ("Quads2RepeatDefault", PhysicalStreamType.QUADS, Quads2RepeatDefault),
    ("Graphs1", PhysicalStreamType.GRAPHS, Graphs1),
  )

  "ProtoTranscoder" should {
    "splice two identical streams" when {
      for (caseName, streamType, testCase) <- testCases do
        s"input is $caseName" in {
          val options: RdfStreamOptions = JellyOptions.smallAllFeatures.withPhysicalType(streamType)
          val input: RdfStreamFrame = testCase.encodedFull(options, 100).head
          val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(options)
          // First frame should be returned as is
          val out1 = transcoder.ingestFrame(input)
          out1 shouldBe input
          // What's more, the rows should be the exact same objects (except the options)
          forAll(input.rows.zip(out1.rows).drop(1)) { case (in, out) =>
            in eq out shouldBe true // reference equality
          }

          val out2 = transcoder.ingestFrame(input)
          out2.rows.size shouldBe < (input.rows.size)
          // No row in out2 should be an options row or a lookup entry row
          forAll(out2.rows) { (row: RdfStreamRow) =>
            row.row.isOptions shouldBe false
            row.row.isPrefix shouldBe false
            row.row.isName shouldBe false
            row.row.isDatatype shouldBe false
          }

          // If there is a row in out2 with same content as in input, it should be the same object
          var identicalRows = 0
          forAll(input.rows) { (row: RdfStreamRow) =>
            val sameRows = out2.rows.filter(_.row == row.row)
            if !sameRows.isEmpty then
              forAtLeast(1, sameRows) { (sameRow: RdfStreamRow) =>
                sameRow eq row shouldBe true
                identicalRows += 1
              }
          }
          // Something should be identical
          identicalRows shouldBe > (0)

          // Decode the output
          val decoder = MockConverterFactory.anyStatementDecoder(None)
          val statements1 = out1.rows.flatMap(decoder.ingestRow)
          val statements2 = out2.rows.flatMap(decoder.ingestRow)
          statements1 shouldBe statements2
        }
    }

    "splice multiple identical streams" when {
      for (caseName, streamType, testCase) <- testCases do
        s"input is $caseName" in {
          val options: RdfStreamOptions = JellyOptions.smallAllFeatures.withPhysicalType(streamType)
          val input: RdfStreamFrame = testCase.encodedFull(options, 100).head
          val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(options)
          val out1 = transcoder.ingestFrame(input)
          var lastOut = out1
          for i <- 1 to 100 do
            val outN = transcoder.ingestFrame(input)
            outN.rows.size shouldBe < (input.rows.size)
            // No row in out should be an options row or a lookup entry row
            forAll(outN.rows) { (row: RdfStreamRow) =>
              row.row.isOptions shouldBe false
              row.row.isPrefix shouldBe false
              row.row.isName shouldBe false
              row.row.isDatatype shouldBe false
            }
            if i != 1 then
              outN shouldBe lastOut
            lastOut = outN
        }
    }

    "splice multiple different streams" when {
      for seed <- 1 to 20 do
        f"random seed is $seed" in {
          val decoder = MockConverterFactory.quadsDecoder(None)
          val options = JellyOptions.smallAllFeatures.withPhysicalType(PhysicalStreamType.QUADS)
          val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(options)
          val possibleCases = Seq(Quads1, Quads2RepeatDefault)
          val random = Random(seed)
          val usedIndices = Array.ofDim[Int](possibleCases.size)
          for i <- 1 to 100 do
            val index = random.nextInt(possibleCases.size)
            usedIndices(index) += 1
            val testCase = possibleCases(index)
            val out = transcoder.ingestFrame(testCase.encodedFull(options, 100).head)

            if usedIndices(index) > 1 then
              // No row in out should be an options row or a lookup entry row
              forAll(out.rows) { (row: RdfStreamRow) =>
                row.row.isOptions shouldBe false
                row.row.isPrefix shouldBe false
                row.row.isName shouldBe false
                row.row.isDatatype shouldBe false
              }

            val decoded = out.rows.flatMap(decoder.ingestRow)
            decoded shouldBe testCase.mrl
        }
    }

    "handle named graphs" in {
      val options = JellyOptions.smallStrict
        .withMaxPrefixTableSize(0)
        .withPhysicalType(PhysicalStreamType.GRAPHS)
        .withVersion(Constants.protoVersion)
      val input = Seq(
        RdfStreamRow(options),
        RdfStreamRow(RdfNameEntry(0, "some IRI")),
        RdfStreamRow(RdfNameEntry(4, "some IRI 2")),
        RdfStreamRow(RdfGraphStart(RdfIri(0, 0))),
        RdfStreamRow(RdfGraphStart(RdfIri(0, 4))),
      )
      val expectedOutput = Seq(
        RdfStreamRow(options),
        RdfStreamRow(RdfNameEntry(0, "some IRI")),
        // ID 4 should be remapped to 2
        RdfStreamRow(RdfNameEntry(0, "some IRI 2")),
        RdfStreamRow(RdfGraphStart(RdfIri(0, 0))),
        RdfStreamRow(RdfGraphStart(RdfIri(0, 0))),
      )
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(options)
      input.flatMap(transcoder.ingestRow) shouldBe expectedOutput
    }

    "remap prefix, name, and datatype IDs" in {
      val options = JellyOptions.smallStrict.withVersion(Constants.protoVersion)
      val input = Seq(
        RdfStreamRow(options),
        RdfStreamRow(RdfNameEntry(4, "some name")),
        RdfStreamRow(RdfPrefixEntry(4, "some prefix")),
        RdfStreamRow(RdfDatatypeEntry(4, "some IRI")),
        RdfStreamRow(RdfTriple(
          RdfTriple(
            RdfIri(4, 4),
            RdfIri(0, 4),
            RdfLiteral("some literal", RdfLiteral.LiteralKind.Datatype(4)),
          ),
          RdfIri(0, 4),
          RdfLiteral("some literal", RdfLiteral.LiteralKind.Datatype(0)),
        )),
        RdfStreamRow(RdfTriple(
          RdfTriple(RdfTerm.Bnode(""), RdfTerm.Bnode(""), RdfTerm.Bnode("")),
          RdfIri(0, 4),
          RdfLiteral("some literal", RdfLiteral.LiteralKind.Datatype(0)),
        )),
      )
      val expectedOutput = Seq(
        RdfStreamRow(options),
        RdfStreamRow(RdfNameEntry(0, "some name")),
        RdfStreamRow(RdfPrefixEntry(0, "some prefix")),
        RdfStreamRow(RdfDatatypeEntry(0, "some IRI")),
        RdfStreamRow(RdfTriple(
          RdfTriple(
            RdfIri(1, 0),
            RdfIri(0, 1),
            RdfLiteral("some literal", RdfLiteral.LiteralKind.Datatype(1)),
          ),
          RdfIri(0, 1),
          RdfLiteral("some literal", RdfLiteral.LiteralKind.Datatype(0)),
        )),
        RdfStreamRow(RdfTriple(
          RdfTriple(RdfTerm.Bnode(""), RdfTerm.Bnode(""), RdfTerm.Bnode("")),
          RdfIri(0, 1),
          RdfLiteral("some literal", RdfLiteral.LiteralKind.Datatype(0)),
        )),
      )
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(options)
      val output = input.flatMap(transcoder.ingestRow)
      output.size shouldBe expectedOutput.size
      for (i <- input.indices) do
        output(i) shouldBe expectedOutput(i)
    }

    "maintain protocol version 1 if input uses it" in {
      val options = JellyOptions.smallStrict.withVersion(Constants.protoVersion_1_0_x)
      val input = RdfStreamRow(options)
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(options.withVersion(Constants.protoVersion))
      val output = transcoder.ingestRow(input)
      output.head shouldBe input
    }

    "throw an exception on a null row" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(JellyOptions.smallStrict)
      val ex = intercept[RdfProtoTranscodingError] {
        transcoder.ingestRow(RdfStreamRow())
      }
      ex.getMessage should include ("Row kind is not set")
    }

    "throw an exception on mismatched physical types if checking is enabled" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoder(
        JellyOptions.defaultSupportedOptions,
        JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES)
      )
      val ex = intercept[RdfProtoTranscodingError] {
        transcoder.ingestRow(RdfStreamRow(
          JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.QUADS)
        ))
      }
      ex.getMessage should include ("Input stream has a different physical type than the output")
      ex.getMessage should include ("PHYSICAL_STREAM_TYPE_QUADS")
      ex.getMessage should include ("PHYSICAL_STREAM_TYPE_TRIPLES")
    }

    "not throw an exception on mismatched physical types if checking is disabled" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(
        JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES)
      )
      transcoder.ingestRow(RdfStreamRow(
        JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.QUADS)
      ))
    }

    "throw an exception on unsupported options if checking is enabled" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoder(
        // Mark the prefix table as disabled
        JellyOptions.defaultSupportedOptions.withMaxPrefixTableSize(0),
        JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES)
      )
      val ex = intercept[RdfProtoDeserializationError] {
        transcoder.ingestRow(RdfStreamRow(
          JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES)
        ))
      }
      ex.getMessage should include ("larger than the maximum supported size")
    }

    "throw an exception if the input does not use prefixes but the output does" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(
        JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES)
      )
      val ex = intercept[RdfProtoTranscodingError] {
        transcoder.ingestRow(RdfStreamRow(
          JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES)
            .withMaxPrefixTableSize(0)
        ))
      }
      ex.getMessage should include ("Output stream uses prefixes, but the input stream does not")
    }

    "accept an input stream with valid options if checking is enabled" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoder(
        // Mark the prefix table as disabled
        JellyOptions.defaultSupportedOptions.withMaxPrefixTableSize(0),
        JellyOptions.smallStrict.withPhysicalType(PhysicalStreamType.TRIPLES).withMaxPrefixTableSize(0),
      )
      val inputOptions = JellyOptions.smallStrict
        .withPhysicalType(PhysicalStreamType.TRIPLES)
        .withMaxPrefixTableSize(0)
      transcoder.ingestRow(RdfStreamRow(inputOptions))
    }

    "preserve lack of metadata in a frame (1.1.1)" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(JellyOptions.smallStrict)
      val input = RdfStreamFrame(
        rows = Seq(RdfStreamRow(
          JellyOptions.smallStrict.withVersion(Constants.protoVersion_1_1_x)
        )),
      )
      val output = transcoder.ingestFrame(input)
      output.metadata.size should be (0)
    }

    "preserve metadata in a frame (1.1.1)" in {
      val transcoder = ProtoTranscoder.fastMergingTranscoderUnsafe(JellyOptions.smallStrict)
      val input = RdfStreamFrame(
        rows = Seq(RdfStreamRow(
          JellyOptions.smallStrict.withVersion(Constants.protoVersion_1_1_x)
        )),
        metadata = Map(
          "key1" -> ByteString.copyFromUtf8("value"),
          "key2" -> ByteString.copyFromUtf8("value2"),
        ),
      )
      val output = transcoder.ingestFrame(input)
      output.metadata.size should be (2)
      output.metadata("key1").toStringUtf8 should be ("value")
      output.metadata("key2").toStringUtf8 should be ("value2")
    }
  }
