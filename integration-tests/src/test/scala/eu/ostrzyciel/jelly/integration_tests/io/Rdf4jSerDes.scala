package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.rdf4j.rio
import eu.ostrzyciel.jelly.convert.rdf4j.rio.JellyWriterSettings
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamType}
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}

import java.io.{InputStream, OutputStream}
import scala.jdk.CollectionConverters.*

implicit def seqMeasure[T]: Measure[Seq[T]] = (seq: Seq[T]) => seq.size

object Rdf4jSerDes extends NativeSerDes[Seq[Statement], Seq[Statement]]:
  val name = "RDF4J"

  private def read(is: InputStream, format: RDFFormat): Seq[Statement] =
    val parser = Rio.createParser(format)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    collector.getStatements.asScala.toSeq

  override def readTriplesW3C(is: InputStream): Seq[Statement] = read(is, RDFFormat.TURTLESTAR)

  override def readQuadsW3C(is: InputStream): Seq[Statement] = read(is, RDFFormat.NQUADS)

  override def readTriplesJelly(is: InputStream): Seq[Statement] = read(is, rio.JELLY)

  override def readQuadsJelly(is: InputStream): Seq[Statement] = read(is, rio.JELLY)

  private def write(os: OutputStream, model: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    val conf = JellyWriterSettings.configFromOptions(opt, frameSize)
    val writer = Rio.createWriter(rio.JELLY, os)
    writer.setWriterConfig(conf)
    writer.startRDF()
    model.foreach(writer.handleStatement)
    writer.endRDF()

  override def writeTriplesJelly(os: OutputStream, model: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    write(os, model, opt.withStreamType(RdfStreamType.TRIPLES), frameSize)

  override def writeQuadsJelly(os: OutputStream, dataset: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    write(os, dataset, opt.withStreamType(RdfStreamType.QUADS), frameSize)





