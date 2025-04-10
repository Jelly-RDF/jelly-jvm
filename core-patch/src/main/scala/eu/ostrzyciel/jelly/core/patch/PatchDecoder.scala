package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.patch.*

import scala.annotation.experimental

@experimental
trait PatchDecoder:
  def getPatchOpt: Option[RdfPatchOptions]

  def ingestRow(row: RdfPatchRow): Unit

  def ingestFrame(frame: RdfPatchFrame): Unit
