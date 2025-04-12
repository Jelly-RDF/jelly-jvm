package eu.ostrzyciel.jelly.integration_tests.patch.traits

import eu.ostrzyciel.jelly.core.proto.v1.patch.RdfPatchOptions
import eu.ostrzyciel.jelly.integration_tests.util.TestComparable

import java.io.{InputStream, OutputStream}

trait JellyPatchImplementation[TPatch : TestComparable]:
  def readJelly(in: InputStream, supportedOptions: Option[RdfPatchOptions] = None): TPatch

  def writeJelly(out: OutputStream, patch: TPatch, options: Option[RdfPatchOptions], frameSize: Int): Unit

  def name: String

  def supportsRdfStar: Boolean = false

  def supportsGeneralizedStatements: Boolean = false
