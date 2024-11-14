package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.rdf4j.rio
import eu.ostrzyciel.jelly.convert.rdf4j.rio.{JellyParserSettings, JellyWriterSettings}
import eu.ostrzyciel.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import java.io.{InputStream, OutputStream}
import scala.jdk.CollectionConverters.*

given seqMeasure[T]: Measure[Seq[T]] = (seq: Seq[T]) => seq.size

object Rdf4jSerDes extends NativeSerDes[Seq[Statement], Seq[Statement]]:
  val name = "RDF4J"

  private def read(is: InputStream, format: RDFFormat, supportedOptions: Option[RdfStreamOptions] = None): 
  Seq[Statement] =
    val parser = Rio.createParser(format)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    supportedOptions.foreach(opt =>
      parser.setParserConfig(JellyParserSettings.configFromOptions(opt))
    )
    parser.parse(is)
    collector.getStatements.asScala.toSeq

  override def readTriplesW3C(is: InputStream): Seq[Statement] = read(is, RDFFormat.TURTLESTAR)

  override def readQuadsW3C(is: InputStream): Seq[Statement] = read(is, RDFFormat.NQUADS)

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] = 
    read(is, rio.JELLY, supportedOptions)

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] = 
    read(is, rio.JELLY, supportedOptions)

  private def write(os: OutputStream, model: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    val conf = JellyWriterSettings.configFromOptions(opt, frameSize)
    val writer = Rio.createWriter(rio.JELLY, os)
    writer.setWriterConfig(conf)
    writer.startRDF()
    model.foreach(writer.handleStatement)
    writer.endRDF()

  override def writeTriplesJelly(os: OutputStream, model: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    // We set the physical type to TRIPLES, because the writer has no way of telling triples from
    // quads in RDF4J. Thus, the writer will default to QUADS.
    write(os, model, opt.withPhysicalType(PhysicalStreamType.TRIPLES), frameSize)

  override def writeQuadsJelly(os: OutputStream, dataset: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    // No need to set the physical type, because the writer will default to QUADS.
    write(os, dataset, opt, frameSize)
