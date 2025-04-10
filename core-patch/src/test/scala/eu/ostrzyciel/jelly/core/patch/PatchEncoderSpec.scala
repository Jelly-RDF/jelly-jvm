package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.JellyExceptions.RdfProtoSerializationError
import eu.ostrzyciel.jelly.core.patch.helpers.MockPatchConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer

@experimental
class PatchEncoderSpec extends AnyWordSpec, Matchers:
  import helpers.Assertions.*
  import eu.ostrzyciel.jelly.core.patch.helpers.PatchTestCases.*
  import PatchEncoder.Params as Pep

  val streamTypes = Seq(PatchStreamType.FLAT, PatchStreamType.FRAME, PatchStreamType.PUNCTUATED)
  
  "a PatchEncoder" should {
    for streamType <- streamTypes do f"with stream type $streamType" when {
      testCases.foreach { case (desc, testCase, statementType) =>
        s"encode $desc" in {
          val buffer = ListBuffer[RdfPatchRow]()
          val encoder = MockPatchConverterFactory.patchEncoder(Pep(
            JellyPatchOptions.smallGeneralized
              .withStatementType(statementType)
              .withStreamType(streamType),
            buffer
          ))
          testCase.mrl.foreach(_.apply(encoder))
          assertEncoded(buffer.toSeq, testCase.encoded(encoder.options))
        }
      }
    }

    "disallow punctuation" when {
      for st <- Seq(PatchStreamType.FLAT, PatchStreamType.FRAME) do
        s"stream type $st" in {
          val buffer = ListBuffer[RdfPatchRow]()
          val encoder = MockPatchConverterFactory.patchEncoder(Pep(
            JellyPatchOptions.smallGeneralized
              .withStatementType(PatchStatementType.TRIPLES)
              .withStreamType(st),
            buffer
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
          val encoder = MockPatchConverterFactory.patchEncoder(Pep(
            JellyPatchOptions.smallGeneralized
              .withStatementType(statementType)
              .withStreamType(PatchStreamType.PUNCTUATED),
            buffer
          ))
          testCase.mrl.foreach(_.apply(encoder))
          encoder.punctuation()
          assertEncoded(
            buffer.toSeq,
            testCase.encoded(encoder.options) :+ RdfPatchRow.ofPunctuation
          )
        }
      }
    }
  }
