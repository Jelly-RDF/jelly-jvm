package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.patch.{RdfPatchOptions, RdfPatchRow}

trait PatchDecoder:
  def getPatchOpt: Option[RdfPatchOptions]
