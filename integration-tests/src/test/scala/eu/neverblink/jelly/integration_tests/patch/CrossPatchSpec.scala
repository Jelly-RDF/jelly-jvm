package eu.neverblink.jelly.integration_tests.patch

import eu.neverblink.jelly.core.patch.{JellyPatchOptions, JellyPatchConstants}
import eu.neverblink.jelly.core.proto.v1.patch.*
import eu.neverblink.jelly.integration_tests.patch.impl.{*, given}
import eu.neverblink.jelly.integration_tests.patch.traits.RdfPatchImplementation
import eu.neverblink.jelly.integration_tests.util.TestComparable
import org.apache.commons.io.output.ByteArrayOutputStream
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.ByteArrayInputStream
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

/**
 * Cross-testing Jelly-Patch implementations in end-to-end test cases.
 */
@experimental
class CrossPatchSpec extends AnyWordSpec, Matchers:
  import eu.neverblink.jelly.core.proto.v1.patch.PatchStatementType.{QUADS, TRIPLES}
  import eu.neverblink.jelly.core.proto.v1.patch.PatchStreamType.{FLAT, FRAME, PUNCTUATED}

  val presets: Seq[(Option[RdfPatchOptions], Int, String)] = Seq(
    (Some(JellyPatchOptions.SMALL_STRICT.clone().setStreamType(FLAT)), 100, "small strict, FLAT"),
    (Some(JellyPatchOptions.SMALL_RDF_STAR.clone().setStreamType(FRAME)), 100, "small RDF-star, FRAME"),
    (Some(JellyPatchOptions.SMALL_GENERALIZED.clone().setStreamType(FRAME)), 10, "small generalized, FRAME"),
    (Some(JellyPatchOptions.SMALL_ALL_FEATURES.clone().setStreamType(PUNCTUATED)), 1000, "small all features, PUNCTUATED"),
    (Some(JellyPatchOptions.BIG_STRICT.clone().setStreamType(PUNCTUATED)), 5, "big strict, PUNCTUATED"),
    (Some(JellyPatchOptions.BIG_RDF_STAR.clone().setStreamType(FLAT)), 5, "big RDF-star, FLAT"),
    (Some(JellyPatchOptions.BIG_GENERALIZED.clone().setStreamType(PUNCTUATED)), 5, "big generalized, PUNCTUATED"),
    (Some(JellyPatchOptions.BIG_ALL_FEATURES.clone().setStreamType(FRAME)), 5, "big all features, FRAME"),
    (None, 10, "no options"),
  )

  // TODO: unsupported presets

  runTest(JenaImplementation, JenaImplementation)

  private def checkOptionsAndPunctuation(
    bytes: Array[Byte],
    expectedOpt: Option[RdfPatchOptions],
    expectedPatches: Int,
  ): Unit =
    val expOpt = expectedOpt.getOrElse(
      JellyPatchOptions.BIG_ALL_FEATURES
        .clone()
        .setStreamType(PUNCTUATED)
        .setStatementType(QUADS)
    )
    val in = ByteArrayInputStream(bytes)
    val frames = Iterator.continually(RdfPatchFrame.parseDelimitedFrom(in))
      .takeWhile(_ != null).toSeq
    // Check option validity
    frames.head.getRows.asScala.head.getRowFieldNumber should be (RdfPatchRow.OPTIONS)
    val opt = frames.head.getRows.asScala.head.getOptions
    opt.getStatementType should be (expOpt.getStatementType)
    opt.getStreamType should be (expOpt.getStreamType)
    opt.getMaxNameTableSize should be (expOpt.getMaxNameTableSize)
    opt.getMaxPrefixTableSize should be (expOpt.getMaxPrefixTableSize)
    opt.getMaxDatatypeTableSize should be (expOpt.getMaxDatatypeTableSize)
    opt.getRdfStar should be (expOpt.getRdfStar)
    opt.getGeneralizedStatements should be (expOpt.getGeneralizedStatements)
    opt.getVersion should be (JellyPatchConstants.PROTO_VERSION_1_0_X)
    // Check punctuation
    val punctMarks = frames.flatMap(_.getRows.asScala).count(_.getRowFieldNumber == RdfPatchRow.PUNCTUATION)
    if expOpt.getStreamType == FRAME then
      frames.size should be (expectedPatches)
      punctMarks should be (0)
    else if expOpt.getStreamType == PUNCTUATED || expOpt.getStreamType == PatchStreamType.UNSPECIFIED then
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
        (name, files) <- TestCases.cases.filter(
          tc => impl1.supportsRdfStar && impl2.supportsRdfStar || !tc._2.exists(f => f.getName.contains("star"))
        )
        (preset, size, presetName) <- presets
      do f"ser/des file $name with preset $presetName, frame size $size" when {
        for
          statementType <- Seq(TRIPLES, QUADS)
        do f"statement type $statementType" in {
          val opt = preset.map(_.clone().setStatementType(statementType))
          val m1 = impl1.readRdf(files, statementType, opt.exists(_.getStreamType == FLAT))
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
