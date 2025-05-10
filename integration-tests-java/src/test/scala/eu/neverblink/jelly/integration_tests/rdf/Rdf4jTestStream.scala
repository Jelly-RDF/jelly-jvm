package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.rdf4j.{Rdf4jConverterFactory, Rdf4jDatatype, Rdf4jDecoderConverter, Rdf4jEncoderConverter}
import eu.neverblink.jelly.core.JellyConverterFactory
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.core.utils.{GraphHolder, QuadExtractor, QuadMaker, TripleExtractor, TripleMaker}
import eu.neverblink.jelly.stream.*
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.{Resource, Statement, Value}
import org.eclipse.rdf4j.rio.*
import org.eclipse.rdf4j.rio.helpers.StatementCollector

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

case object Rdf4jTestStream extends TestStream:
  given JellyConverterFactory[Value, Rdf4jDatatype, Rdf4jEncoderConverter, Rdf4jDecoderConverter] = Rdf4jConverterFactory.getInstance()

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
      .map(e => GraphHolder[Value, Statement](e._1, e._2.asJava))
    
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
