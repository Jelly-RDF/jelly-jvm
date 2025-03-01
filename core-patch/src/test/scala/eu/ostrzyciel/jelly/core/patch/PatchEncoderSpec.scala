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

  val testCases = Seq(
    ("a triple patch", Triples1, PatchPhysicalType.TRIPLES),
    ("a triple patch with namespace declarations", Triples2NsDecl, PatchPhysicalType.TRIPLES),
    ("a quad patch", Quads1, PatchPhysicalType.QUADS),
    ("nonsensical transaction commands", MalformedTransactions, PatchPhysicalType.TRIPLES),
  )
  
  "a PatchEncoder" should {
    testCases.foreach { case (desc, testCase, physicalType) =>
      s"encode $desc" in {
        val buffer = ListBuffer[RdfPatchRow]()
        val encoder = MockPatchConverterFactory.patchEncoder(Pep(
          JellyPatchOptions.smallGeneralized.withPhysicalType(physicalType),
          buffer
        ))
        testCase.mrl.foreach(_.apply(encoder))
        assertEncoded(buffer.toSeq, testCase.encoded(encoder.options))
      }
    }
  }
