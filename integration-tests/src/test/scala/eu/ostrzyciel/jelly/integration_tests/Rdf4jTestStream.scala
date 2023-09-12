package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.ostrzyciel.jelly.stream.{DecoderFlow, EncoderFlow}
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.rio.*
import org.eclipse.rdf4j.rio.helpers.StatementCollector

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

case object Rdf4jTestStream extends TestStream:
  import eu.ostrzyciel.jelly.convert.rdf4j.*

  override def tripleSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    // This buffers everything in memory... but I'm too lazy to implement my own RDFHandler for this
    // RDF4J at the moment only has two formats with RDF-star support – Turtle and Trig.
    val parser = Rio.createParser(RDFFormat.TURTLESTAR)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    Source.fromIterator(() => collector.getStatements.asScala.iterator)
      .via(EncoderFlow.fromFlatTriples(streamOpt, jellyOpt))

  override def quadSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    val parser = Rio.createParser(RDFFormat.NQUADS)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    Source.fromIterator(() => collector.getStatements.asScala.iterator)
      .via(EncoderFlow.fromFlatQuads(streamOpt, jellyOpt))

  override def graphSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    val parser = Rio.createParser(RDFFormat.NQUADS)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    val graphs = collector.getStatements.asScala.toSeq
      .groupBy(_.getContext)
    Source.fromIterator(() => graphs.iterator)
      .via(EncoderFlow.fromGraphs(streamOpt, jellyOpt))

  override def tripleSink(os: OutputStream)(implicit ec: ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.TURTLESTAR, os)
    writer.startRDF()
    Flow[RdfStreamFrame]
      .via(DecoderFlow.triplesToFlat)
      .toMat(Sink.foreach(st => writer.handleStatement(st)))(Keep.right)
      .mapMaterializedValue(f => f.map(_ => {
        writer.endRDF()
        Done
      }))

  override def quadSink(os: OutputStream)(implicit ec: ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.NQUADS, os)
    writer.startRDF()
    Flow[RdfStreamFrame]
      .via(DecoderFlow.quadsToFlat)
      .toMat(Sink.foreach(st => writer.handleStatement(st)))(Keep.right)
      .mapMaterializedValue(f => f.map(_ => {
        writer.endRDF()
        Done
      }))

  override def graphSink(os: OutputStream)(implicit ec: ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.NQUADS, os)
    writer.startRDF()
    Flow[RdfStreamFrame]
      .via(DecoderFlow.graphsAsQuadsToFlat)
      .toMat(Sink.foreach(st => writer.handleStatement(st)))(Keep.right)
      .mapMaterializedValue(f => f.map(_ => {
        writer.endRDF()
        Done
      }))
