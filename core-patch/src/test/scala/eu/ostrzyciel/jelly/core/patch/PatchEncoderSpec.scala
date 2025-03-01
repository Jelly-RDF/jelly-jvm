package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.patch.helpers.Assertions.*
import eu.ostrzyciel.jelly.core.patch.helpers.MockPatchConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer

class PatchEncoderSpec extends AnyWordSpec, Matchers:
  import eu.ostrzyciel.jelly.core.patch.helpers.PatchTestCases.*
  import PatchEncoder.Params as Pep
  
  "a PatchEncoder" should {
    "encode a triple patch" in {
      val buffer = ListBuffer[RdfPatchRow]()
      val encoder = MockPatchConverterFactory.patchEncoder(Pep(
        JellyPatchOptions.smallGeneralized.withPhysicalType(PatchPhysicalType.TRIPLES),
        buffer
      ))
      Triples1.mrl.foreach(_.apply(encoder))
      assertEncoded(buffer.toSeq, Triples1.encoded(encoder.options.withVersion(PatchConstants.protoVersion)))
    }

    "encode a triple patch with namespace declarations" in {
      val buffer = ListBuffer[RdfPatchRow]()
      val encoder = MockPatchConverterFactory.patchEncoder(Pep(
        JellyPatchOptions.smallGeneralized.withPhysicalType(PatchPhysicalType.TRIPLES),
        buffer,
      ))
      Triples2NsDecl.mrl.foreach(_.apply(encoder))
      assertEncoded(buffer.toSeq, Triples2NsDecl.encoded(encoder.options))
    }
  }
