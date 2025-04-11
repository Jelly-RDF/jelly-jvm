package eu.ostrzyciel.jelly.core.patch

import scala.annotation.experimental

@experimental
object PatchConstants:
  val jellyPatchName = "Jelly-Patch"
  val jellyPatchFileExtension = "jelly-patch"
  val jellyPatchContentType = "application/x-jelly-rdf-patch"
  val protoVersion_1_0_x = 1
  val protoVersion: Int = protoVersion_1_0_x
  val protoSemanticVersion_1_0_0 = "1.0.0" // First protocol version, based on Jelly-RDF 1.1.x
  val protoSemanticVersion: String = protoSemanticVersion_1_0_0
