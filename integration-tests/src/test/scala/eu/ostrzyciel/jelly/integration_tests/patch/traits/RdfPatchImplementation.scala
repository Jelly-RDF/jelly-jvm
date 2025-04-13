package eu.ostrzyciel.jelly.integration_tests.patch.traits

import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStatementType
import eu.ostrzyciel.jelly.integration_tests.util.TestComparable

import java.io.{File, InputStream}

trait RdfPatchImplementation[TPatch : TestComparable] extends JellyPatchImplementation[TPatch]:
  def readRdf(in: InputStream, stType: PatchStatementType): TPatch

  def readRdf(filenames: Seq[File], stType: PatchStatementType, flat: Boolean): TPatch
