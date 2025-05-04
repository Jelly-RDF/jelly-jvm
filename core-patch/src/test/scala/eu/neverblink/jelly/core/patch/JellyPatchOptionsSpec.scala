package eu.neverblink.jelly.core.patch

import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental

@experimental
class JellyPatchOptionsSpec extends AnyWordSpec, Matchers:
  "JellyPatchOptions" should {
    val optionCases = Seq(
      (JellyPatchOptions.BIG_STRICT, JellyOptions.BIG_STRICT),
      (JellyPatchOptions.BIG_GENERALIZED, JellyOptions.BIG_GENERALIZED),
      (JellyPatchOptions.BIG_RDF_STAR, JellyOptions.BIG_RDF_STAR),
      (JellyPatchOptions.BIG_ALL_FEATURES, JellyOptions.BIG_ALL_FEATURES),
      (JellyPatchOptions.SMALL_STRICT, JellyOptions.SMALL_STRICT),
      (JellyPatchOptions.SMALL_GENERALIZED, JellyOptions.SMALL_GENERALIZED),
      (JellyPatchOptions.SMALL_RDF_STAR, JellyOptions.SMALL_RDF_STAR),
      (JellyPatchOptions.SMALL_ALL_FEATURES, JellyOptions.SMALL_ALL_FEATURES),
      (JellyPatchOptions.DEFAULT_SUPPORTED_OPTIONS, JellyOptions.DEFAULT_SUPPORTED_OPTIONS),
    )

    for ((opt, jellyOpt), i) <- optionCases.zipWithIndex do
      f"have corresponding options to JellyOptions, case $i" in {
        opt.getGeneralizedStatements should be (jellyOpt.getGeneralizedStatements)
        opt.getRdfStar should be (jellyOpt.getRdfStar)
        opt.getMaxNameTableSize should be (jellyOpt.getMaxNameTableSize)
        opt.getMaxPrefixTableSize should be (jellyOpt.getMaxPrefixTableSize)
        opt.getMaxDatatypeTableSize should be (jellyOpt.getMaxDatatypeTableSize)
        opt.getVersion should be (1)
      }

    val physicalTypeCases = Seq(
      (PatchStatementType.UNSPECIFIED, PhysicalStreamType.UNSPECIFIED),
      (PatchStatementType.TRIPLES, PhysicalStreamType.TRIPLES),
      (PatchStatementType.QUADS, PhysicalStreamType.GRAPHS),
      (PatchStatementType.QUADS, PhysicalStreamType.QUADS),
    )

    for (patchType, jellyType) <- physicalTypeCases do
      f"convert PhysicalStreamType to StatementType, case $jellyType" in {
        val opt = JellyOptions.DEFAULT_SUPPORTED_OPTIONS.clone()
          .setPhysicalType(jellyType)
        
        val patchOpt = JellyPatchOptions.fromJellyOptions(opt)
        patchOpt.getStatementType should be (patchType)
      }
  }
