package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.PhysicalStreamType
import eu.ostrzyciel.jelly.core.proto.v1.patch.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.annotation.experimental

@experimental
class JellyPatchOptionsSpec extends AnyWordSpec, Matchers:
  "JellyPatchOptions" should {
    val optionCases = Seq(
      (JellyPatchOptions.bigStrict, JellyOptions.bigStrict),
      (JellyPatchOptions.bigGeneralized, JellyOptions.bigGeneralized),
      (JellyPatchOptions.bigRdfStar, JellyOptions.bigRdfStar),
      (JellyPatchOptions.bigAllFeatures, JellyOptions.bigAllFeatures),
      (JellyPatchOptions.smallStrict, JellyOptions.smallStrict),
      (JellyPatchOptions.smallGeneralized, JellyOptions.smallGeneralized),
      (JellyPatchOptions.smallRdfStar, JellyOptions.smallRdfStar),
      (JellyPatchOptions.smallAllFeatures, JellyOptions.smallAllFeatures),
      (JellyPatchOptions.defaultSupportedOptions, JellyOptions.defaultSupportedOptions),
    )

    for ((opt, jellyOpt), i) <- optionCases.zipWithIndex do
      f"have corresponding options to JellyOptions, case $i" in {
        opt.generalizedStatements should be (jellyOpt.generalizedStatements)
        opt.rdfStar should be (jellyOpt.rdfStar)
        opt.maxNameTableSize should be (jellyOpt.maxNameTableSize)
        opt.maxPrefixTableSize should be (jellyOpt.maxPrefixTableSize)
        opt.maxDatatypeTableSize should be (jellyOpt.maxDatatypeTableSize)
        opt.version should be (1)
      }

    val physicalTypeCases = Seq(
      (PatchStatementType.UNSPECIFIED, PhysicalStreamType.UNSPECIFIED),
      (PatchStatementType.TRIPLES, PhysicalStreamType.TRIPLES),
      (PatchStatementType.QUADS, PhysicalStreamType.GRAPHS),
      (PatchStatementType.QUADS, PhysicalStreamType.QUADS),
    )

    for (patchType, jellyType) <- physicalTypeCases do
      f"convert PhysicalStreamType to StatementType, case $jellyType" in {
        val opt = JellyOptions.defaultSupportedOptions.withPhysicalType(jellyType)
        val patchOpt = JellyPatchOptions.fromJellyOptions(opt)
        patchOpt.statementType should be (patchType)
      }
  }
