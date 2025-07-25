package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.patch.helpers.MockPatchConverterFactory
import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.memory.EncoderAllocator
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*
import eu.neverblink.protoc.java.runtime.{ArrayListMessageCollection, MessageFactory}

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

@experimental
class PatchEncoderSpec extends AnyWordSpec, Matchers:
  import helpers.Assertions.*
  import eu.neverblink.jelly.core.patch.helpers.PatchTestCases.*
  import PatchEncoder.Params as Pep

  val streamTypes = Seq(PatchStreamType.FLAT, PatchStreamType.FRAME, PatchStreamType.PUNCTUATED)

  private def getBuffer =
    ArrayListMessageCollection[RdfPatchRow, RdfPatchRow.Mutable](
      () => RdfPatchRow.newInstance()
    )
  
  "PatchEncoder" should {
    for streamType <- streamTypes do f"with stream type $streamType" when {
      testCases.foreach { case (desc, testCase, statementType) =>
        s"encode $desc" in {
          val buffer = getBuffer
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.clone
              .setStatementType(statementType)
              .setStreamType(streamType),
            buffer,
            EncoderAllocator.newHeapAllocator()
          ))
          testCase.mrl.foreach(_.apply(encoder))
          assertEncoded(buffer.asScala.toSeq, testCase.encoded(encoder.options))
        }

        s"precompute the size of each patch row ($desc)" in {
          val buffer = getBuffer
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.clone
              .setStatementType(statementType)
              .setStreamType(streamType),
            buffer,
            EncoderAllocator.newHeapAllocator()
          ))
          testCase.mrl.foreach(_.apply(encoder))
          for (row, ix) <- buffer.asScala.zipWithIndex do
            withClue(s"Row $ix: ${row.getRow}") {
              row.getCachedSize should be > 0
            }
        }
      }
    }

    "disallow punctuation" when {
      for st <- Seq(PatchStreamType.FLAT, PatchStreamType.FRAME) do
        s"stream type $st" in {
          val buffer = getBuffer
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.clone
              .setStatementType(PatchStatementType.TRIPLES)
              .setStreamType(st),
            buffer,
            EncoderAllocator.newHeapAllocator()
          ))
          val e = intercept[RdfProtoSerializationError] {
            encoder.punctuation()
          }
          e.getMessage should include("is not allowed in this stream type")
        }
    }

    "encode punctuation in stream type PUNCTUATED" when {
      testCases.foreach { case (desc, testCase, statementType) =>
        s"encoding $desc" in {
          val buffer = getBuffer
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_STRICT.clone
              .setStatementType(statementType)
              .setStreamType(PatchStreamType.PUNCTUATED),
            buffer,
            EncoderAllocator.newHeapAllocator()
          ))
          testCase.mrl.foreach(_.apply(encoder))
          encoder.punctuation()
          assertEncoded(
            buffer.asScala.toSeq,
            testCase.encoded(encoder.options) :+ rdfPatchRow(rdfPatchPunctuation())
          )
        }
      }
    }

    "clone the provided options and override the version" in {
      val options = JellyPatchOptions.SMALL_GENERALIZED
        .clone
        .setStatementType(PatchStatementType.TRIPLES)
        .setStreamType(PatchStreamType.PUNCTUATED)
        .setVersion(123)
      val buffer = getBuffer
      val encoder = MockPatchConverterFactory.encoder(Pep(
        options,
        buffer,
        EncoderAllocator.newHeapAllocator()
      ))
      encoder.punctuation() // write anything
      buffer.size shouldBe 2
      buffer.asScala.head.getOptions shouldNot be theSameInstanceAs options
      buffer.asScala.head.getOptions.getVersion shouldBe JellyPatchConstants.PROTO_VERSION_1_0_X
      options.getVersion shouldBe 123 // original options should not be modified
    }
  }
