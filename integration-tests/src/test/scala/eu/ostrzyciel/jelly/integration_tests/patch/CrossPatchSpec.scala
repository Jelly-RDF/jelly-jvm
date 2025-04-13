package eu.ostrzyciel.jelly.integration_tests.patch

import eu.ostrzyciel.jelly.core.patch.{JellyPatchOptions, PatchConstants}
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import eu.ostrzyciel.jelly.integration_tests.patch.impl.{*, given}
import eu.ostrzyciel.jelly.integration_tests.patch.traits.RdfPatchImplementation
import eu.ostrzyciel.jelly.integration_tests.util.TestComparable
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayInputStream
import scala.annotation.experimental

@experimental
class CrossPatchSpec extends AnyWordSpec, Matchers:
  import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStatementType.{QUADS, TRIPLES}
  import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStreamType.{FLAT, FRAME, PUNCTUATED}

  val presets: Seq[(Option[RdfPatchOptions], Int, String)] = Seq(
    (Some(JellyPatchOptions.smallStrict.copy(streamType = FLAT)), 100, "small strict, FLAT"),
    (Some(JellyPatchOptions.smallRdfStar.copy(streamType = FRAME)), 100, "small RDF-star, FRAME"),
    (Some(JellyPatchOptions.smallGeneralized.copy(streamType = FRAME)), 10, "small generalized, FRAME"),
    (Some(JellyPatchOptions.smallAllFeatures.copy(streamType = PUNCTUATED)), 1000, "small all features, PUNCTUATED"),
    (Some(JellyPatchOptions.bigStrict.copy(streamType = PUNCTUATED)), 5, "big strict, PUNCTUATED"),
    (Some(JellyPatchOptions.bigRdfStar.copy(streamType = FLAT)), 5, "big RDF-star, FLAT"),
    (Some(JellyPatchOptions.bigGeneralized.copy(streamType = PUNCTUATED)), 5, "big generalized, PUNCTUATED"),
    (Some(JellyPatchOptions.bigAllFeatures.copy(streamType = FRAME)), 5, "big all features, FRAME"),
    (None, 10, "no options"),
  )

  // TODO: unsupported presets
  
  runTest(JenaImplementation, JenaImplementation)

  private def checkOptionsAndPunctuation(
    bytes: Array[Byte],
    expectedOpt: Option[RdfPatchOptions],
    expectedPatches: Int,
  ): Unit =
    val expOpt = expectedOpt.getOrElse(JellyPatchOptions.bigAllFeatures.copy(
      streamType = PUNCTUATED,
      statementType = QUADS,
    ))
    val in = ByteArrayInputStream(bytes)
    val frames = Iterator.continually(RdfPatchFrame.parseDelimitedFrom(in))
      .takeWhile(_.isDefined).map(_.get).toSeq
    // Check option validity
    frames.head.rows.head.rowType should be (RdfPatchRow.OPTIONS_FIELD_NUMBER)
    val opt = frames.head.rows.head.row.asInstanceOf[RdfPatchOptions]
    opt.statementType should be (expOpt.statementType)
    opt.streamType should be (expOpt.streamType)
    opt.maxNameTableSize should be (expOpt.maxNameTableSize)
    opt.maxPrefixTableSize should be (expOpt.maxPrefixTableSize)
    opt.maxDatatypeTableSize should be (expOpt.maxDatatypeTableSize)
    opt.rdfStar should be (expOpt.rdfStar)
    opt.generalizedStatements should be (expOpt.generalizedStatements)
    opt.version should be (PatchConstants.protoVersion_1_0_x)
    // Check punctuation
    val punctMarks = frames.flatMap(_.rows).count(_.rowType == RdfPatchRow.PUNCTUATION_FIELD_NUMBER)
    if expOpt.streamType.isFrame then
      frames.size should be (expectedPatches)
      punctMarks should be (0)
    else if expOpt.streamType.isPunctuated || expOpt.streamType.isUnspecified then
      punctMarks should be (expectedPatches)
    else // FLAT
      punctMarks should be (0)

  private def runTest[T1 : TestComparable, T2 : TestComparable](
    impl1: RdfPatchImplementation[T1],
    impl2: RdfPatchImplementation[T2],
  ): Unit =
    val c1 = summon[TestComparable[T1]]
    val c2 = summon[TestComparable[T2]]
    f"${impl1.name} serializer + ${impl2.name} deserializer" should {
      for
        (name, files) <- TestCases.cases
        (preset, size, presetName) <- presets
      do f"ser/des file $name with preset $presetName, frame size $size" when {
        for
          statementType <- Seq(TRIPLES, QUADS)
        do f"statement type $statementType" in {
          val opt = preset.map(_.withStatementType(statementType))
          val m1 = impl1.readRdf(files, statementType, opt.exists(_.streamType.isFlat))
          val originalSize = c1.size(m1)
          originalSize should be > 0L

          val os = ByteArrayOutputStream()
          impl1.writeJelly(os, m1, opt, size)
          os.close()
          val bytes = os.toByteArray
          bytes.size should be > 0
          checkOptionsAndPunctuation(bytes, opt, files.size)

          // Try parsing what impl1 wrote with impl2
          val m2 = impl2.readJelly(ByteArrayInputStream(bytes), None)
          c2.size(m2) shouldEqual originalSize

          // If the two implementations are comparable directly, do that
          // This won't work if no options were set, as the serializer will automatically infer the
          // stream to be QUADS.
          if c1 == c2 && opt.isDefined then
            c1.compare(m1, m2.asInstanceOf[T1])

          // We additionally round-trip data from impl2 to impl2...
          val os2 = ByteArrayOutputStream()
          impl2.writeJelly(os2, m2, opt, size)
          os2.close()
          val bytes2 = os2.toByteArray
          bytes2.size should be > 0

          val m2_b = impl2.readJelly(ByteArrayInputStream(bytes2), None)
          c2.compare(m2, m2_b) // Compare the two impl2 outputs
          checkOptionsAndPunctuation(bytes2, opt, files.size)

          // ...and from impl2 to impl1
          val m1_b = impl1.readJelly(ByteArrayInputStream(bytes2), None)
          // Compare the two impl1 outputs
          if opt.isDefined then
            c1.compare(m1, m1_b)
        }
      }
    }
