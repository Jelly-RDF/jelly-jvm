package eu.ostrzyciel.jelly.core.patch

import eu.ostrzyciel.jelly.core.proto.v1.patch.{RdfPatchOptions, RdfPatchRow}

trait PatchDecoder[+TOut]:
  def getPatchOpt: Option[RdfPatchOptions]
  
  def ingestRow(row: RdfPatchRow): Option[TOut]
