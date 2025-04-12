package eu.ostrzyciel.jelly.integration_tests.rdf.util

import com.google.protobuf.ByteString
import eu.ostrzyciel.jelly.convert.jena.riot.{JellyFormatVariant, JellyLanguage}
import eu.ostrzyciel.jelly.core.Constants
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame
import eu.ostrzyciel.jelly.integration_tests.rdf.BackCompatSpec.testCases
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFFormat}
import org.apache.jena.sparql.core.DatasetGraphFactory

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
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
        // enable this to make this into a Jelly 1.1.x file
        JellyFormatVariant(enableNamespaceDeclarations = true)
      )
      // First write the data to a buffer
      val bufferOs = ByteArrayOutputStream()
      RDFDataMgr.write(
        bufferOs,
        jenaDg,
        v2Format
      )
      // Parse it, add some metadata (not supported by RDF4J writer) and write it to a file
      // This was added in Jelly-RDF 1.1.1
      val fileOs = Files.newOutputStream(Path.of(
        s"integration-tests/src/test/resources/backcompat/${fileName}_v$version.jelly"
      ))
      val bufferIs = ByteArrayInputStream(bufferOs.toByteArray)
      Iterator
        .continually(RdfStreamFrame.parseDelimitedFrom(bufferIs))
        .takeWhile(_.isDefined)
        .map(maybeFrame => maybeFrame.get.withMetadata(Map(
          "keyString" -> ByteString.copyFromUtf8("valueString"),
          "keyLowBytes" -> ByteString.copyFrom(Array[Byte](1, 2, 3, 4, 5)),
          "keyZeroes" -> ByteString.copyFrom(Array.ofDim[Byte](40)),
        )))
        .foreach(frame => frame.writeDelimitedTo(fileOs))
      fileOs.close()
      println(s"Generated ${fileName}_v$version.jelly")
