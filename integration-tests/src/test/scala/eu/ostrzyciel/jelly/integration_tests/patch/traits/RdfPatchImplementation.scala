package eu.ostrzyciel.jelly.integration_tests.patch.traits

import eu.ostrzyciel.jelly.integration_tests.util.TestComparable

import java.io.{File, InputStream}

trait RdfPatchImplementation[TPatch : TestComparable] extends JellyPatchImplementation[TPatch]:
  def readRdf(in: InputStream): TPatch
  
  def readRdf(filenames: Seq[File], flat: Boolean): TPatch
