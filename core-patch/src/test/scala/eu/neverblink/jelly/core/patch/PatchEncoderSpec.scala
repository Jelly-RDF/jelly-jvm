package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.patch.helpers.MockPatchConverterFactory
import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.proto.v1.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import eu.neverblink.jelly.core.helpers.RdfAdapter.*
import eu.neverblink.jelly.core.patch.helpers.PatchAdapter.*

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

@experimental
class PatchEncoderSpec extends AnyWordSpec, Matchers:
  import helpers.Assertions.*
  import eu.neverblink.jelly.core.patch.helpers.PatchTestCases.*
  import PatchEncoder.Params as Pep

  val streamTypes = Seq(PatchStreamType.PATCH_STREAM_TYPE_FLAT, PatchStreamType.PATCH_STREAM_TYPE_FRAME, PatchStreamType.PATCH_STREAM_TYPE_PUNCTUATED)
  
  "PatchEncoder" should {
    for streamType <- streamTypes do f"with stream type $streamType" when {
      testCases.foreach { case (desc, testCase, statementType) =>
        s"encode $desc" in {
          val buffer = ListBuffer[RdfPatchRow]()
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.toBuilder
              .setStatementType(statementType)
              .setStreamType(streamType)
              .build(),
            buffer.asJava
          ))
          testCase.mrl.foreach(_.apply(encoder))
          assertEncoded(buffer.toSeq, testCase.encoded(encoder.options))
        }
      }
    }

    "disallow punctuation" when {
      for st <- Seq(PatchStreamType.PATCH_STREAM_TYPE_FLAT, PatchStreamType.PATCH_STREAM_TYPE_FRAME) do
        s"stream type $st" in {
          val buffer = ListBuffer[RdfPatchRow]()
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.toBuilder
              .setStatementType(PatchStatementType.PATCH_STATEMENT_TYPE_TRIPLES)
              .setStreamType(st)
              .build(),
            buffer.asJava
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
          val buffer = ListBuffer[RdfPatchRow]()
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_STRICT.toBuilder
              .setStatementType(statementType)
              .setStreamType(PatchStreamType.PATCH_STREAM_TYPE_PUNCTUATED)
              .build(),
            buffer.asJava
          ))
          testCase.mrl.foreach(_.apply(encoder))
          encoder.punctuation()
          assertEncoded(
            buffer.toSeq,
            testCase.encoded(encoder.options) :+ rdfPatchRow(rdfPatchPunctuation())
          )
        }
      }
    }
  }
