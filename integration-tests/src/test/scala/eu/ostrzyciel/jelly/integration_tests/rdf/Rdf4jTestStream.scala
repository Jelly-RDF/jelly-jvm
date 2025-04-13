package eu.ostrzyciel.jelly.integration_tests.rdf

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.ostrzyciel.jelly.stream.*
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.rio.*
import org.eclipse.rdf4j.rio.helpers.StatementCollector

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

case object Rdf4jTestStream extends TestStream:
  import eu.ostrzyciel.jelly.convert.rdf4j.given

  override def tripleSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    // This buffers everything in memory... but I'm too lazy to implement my own RDFHandler for this
    // RDF4J at the moment only has two formats with RDF-star support – Turtle and Trig.
    val parser = Rio.createParser(RDFFormat.TURTLESTAR)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    Source.fromIterator(() => collector.getStatements.asScala.iterator)
      .via(EncoderFlow.builder.withLimiter(limiter).flatTriples(jellyOpt).flow)

  override def quadSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    val parser = Rio.createParser(RDFFormat.NQUADS)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    Source.fromIterator(() => collector.getStatements.asScala.iterator)
      .via(EncoderFlow.builder.withLimiter(limiter).flatQuads(jellyOpt).flow)

  override def graphSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    val parser = Rio.createParser(RDFFormat.NQUADS)
    val collector = new StatementCollector()
    parser.setRDFHandler(collector)
    parser.parse(is)
    val graphs = collector.getStatements.asScala.toSeq
      .groupBy(_.getContext)
    Source.fromIterator(() => graphs.iterator)
      .via(EncoderFlow.builder.withLimiter(limiter).namedGraphs(jellyOpt).flow)

  override def tripleSink(os: OutputStream)(using ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.TURTLESTAR, os)
    writer.startRDF()
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeTriples.asFlatTripleStream)
      .toMat(Sink.foreach(st => writer.handleStatement(st)))(Keep.right)
      .mapMaterializedValue(f => f.map(_ => {
        writer.endRDF()
        Done
      }))

  override def quadSink(os: OutputStream)(using ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.NQUADS, os)
    writer.startRDF()
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeQuads.asFlatQuadStream)
      .toMat(Sink.foreach(st => writer.handleStatement(st)))(Keep.right)
      .mapMaterializedValue(f => f.map(_ => {
        writer.endRDF()
        Done
      }))

  override def graphSink(os: OutputStream)(using ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.NQUADS, os)
    writer.startRDF()
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeGraphs.asFlatQuadStream)
      .toMat(Sink.foreach(st => writer.handleStatement(st)))(Keep.right)
      .mapMaterializedValue(f => f.map(_ => {
        writer.endRDF()
        Done
      }))
