package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.patch.helpers.MockPatchConverterFactory
import eu.neverblink.jelly.core.RdfProtoSerializationError
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.patch.*
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

  val streamTypes = Seq(PatchStreamType.FLAT, PatchStreamType.FRAME, PatchStreamType.PUNCTUATED)
  
  "PatchEncoder" should {
    for streamType <- streamTypes do f"with stream type $streamType" when {
      testCases.foreach { case (desc, testCase, statementType) =>
        s"encode $desc" in {
          val buffer = ListBuffer[RdfPatchRow]()
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.clone
              .setStatementType(statementType)
              .setStreamType(streamType),
            buffer.asJava
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
          val encoder = MockPatchConverterFactory.encoder(Pep(
            JellyPatchOptions.SMALL_GENERALIZED.clone
              .setStatementType(PatchStatementType.TRIPLES)
              .setStreamType(st),
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
            JellyPatchOptions.SMALL_STRICT.clone
              .setStatementType(statementType)
              .setStreamType(PatchStreamType.PUNCTUATED),
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

    "clone the provided options and override the version" in {
      val options = JellyPatchOptions.SMALL_GENERALIZED
        .clone
        .setStatementType(PatchStatementType.TRIPLES)
        .setStreamType(PatchStreamType.PUNCTUATED)
        .setVersion(123)
      val buffer = ListBuffer[RdfPatchRow]()
      val encoder = MockPatchConverterFactory.encoder(Pep(
        options,
        buffer.asJava
      ))
      encoder.punctuation() // write anything
      buffer.size shouldBe 2
      buffer.head.getOptions shouldNot be theSameInstanceAs options
      buffer.head.getOptions.getVersion shouldBe JellyPatchConstants.PROTO_VERSION_1_0_X
      options.getVersion shouldBe 123 // original options should not be modified
    }
  }
