package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.ProtoTestCases.*
import eu.ostrzyciel.jelly.core.helpers.{MockConverterFactory, Mrl}
import eu.ostrzyciel.jelly.core.proto.v1.*
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtoTranscoderSpec extends AnyWordSpec, Inspectors, Matchers:
  def smallOptions(prefixTableSize: Int) = RdfStreamOptions(
    maxNameTableSize = 4,
    maxPrefixTableSize = prefixTableSize,
    maxDatatypeTableSize = 8,
  )

  "ProtoTranscoder" should {
    "splice two identical streams" when {
      "input is Triples1" in {
        // TODO: more cases
        val options: RdfStreamOptions = JellyOptions.smallAllFeatures.withPhysicalType(PhysicalStreamType.TRIPLES)
        val input: RdfStreamFrame = Triples1.encodedFull(options, 100).head
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
          val sameRow = out2.rows.find(_.row == row.row)
          if (sameRow.isDefined) {
            sameRow.get eq row shouldBe true
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

    // TODO: same, with more repetitions

    // TODO: splicing multiple different streams in various orders

    // TODO: exception handling
  }