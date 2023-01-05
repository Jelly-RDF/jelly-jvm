package pl.ostrzyciel.jelly.integration_tests

import akka.{Done, NotUsed}
import akka.stream.scaladsl.*
import org.eclipse.rdf4j.rio.*
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import pl.ostrzyciel.jelly.core.proto.{RdfStreamFrame, RdfStreamOptions}
import pl.ostrzyciel.jelly.stream.{DecoderFlow, EncoderFlow}

import java.io.{InputStream, OutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

case object Rdf4jTestStream extends TestStream:
  import pl.ostrzyciel.jelly.convert.rdf4j.*

  override def tripleSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    // This buffers everything in memory... but I'm too lazy to implement my own RDFHandler for this
    val parser = Rio.createParser(RDFFormat.NTRIPLES)
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

  override def tripleSink(os: OutputStream)(implicit ec: ExecutionContext) =
    val writer = Rio.createWriter(RDFFormat.NTRIPLES, os)
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
