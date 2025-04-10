package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.patch.{RdfPatchFrame, RdfPatchOptions}

import scala.annotation.experimental

@experimental
trait PatchFrameDecoder:
  def getPatchOpt: Option[RdfPatchOptions]

  def ingestFrame(frame: RdfPatchFrame): Unit
