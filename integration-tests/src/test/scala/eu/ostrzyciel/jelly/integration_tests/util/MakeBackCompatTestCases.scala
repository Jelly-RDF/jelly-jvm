package eu.ostrzyciel.jelly.integration_tests.util

import eu.ostrzyciel.jelly.convert.jena.riot.{JellyFormatVariant, JellyLanguage}
import eu.ostrzyciel.jelly.core.Constants
import eu.ostrzyciel.jelly.integration_tests.BackCompatSpec.testCases
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat}
import org.apache.jena.sparql.core.DatasetGraphFactory

import java.nio.file.{Files, Path}

/**
 * Utility to generate *.jelly files for later back-compat testing.
 * These live in resources/backcompat.
 *
 * Run this utility after increasing the protocol version in the Constants class.
 */
object MakeBackCompatTestCases:

  @main
  def runMakeBackCompatTestCases(): Unit =
    val version = Constants.protoSemanticVersion.replace(".", "_")
    for (fileName, description, versions) <- testCases do
      val jenaDg = DatasetGraphFactory.create()
      RDFDataMgr.read(
        jenaDg,
        getClass.getResourceAsStream(s"/backcompat/$fileName.trig"),
        Lang.TRIG
      )
      val v2Format = new RDFFormat(
        JellyLanguage.JELLY,
        // enable this to make this into a Jelly 1.1.0 file
        JellyFormatVariant(enableNamespaceDeclarations = true)
      )
      RDFDataMgr.write(
        Files.newOutputStream(Path.of(s"integration-tests/src/test/resources/backcompat/${fileName}_v$version.jelly")),
        jenaDg,
        v2Format
      )
      println(s"Generated ${fileName}_v$version.jelly")
