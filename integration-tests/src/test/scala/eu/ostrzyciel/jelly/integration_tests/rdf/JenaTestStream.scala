package eu.ostrzyciel.jelly.integration_tests.rdf

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.riot.system.AsyncParser
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

case object JenaTestStream extends TestStream:
  import eu.ostrzyciel.jelly.convert.jena.given

  override def tripleSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    Source.fromIterator(() => AsyncParser.asyncParseTriples(is, Lang.TURTLE, "").asScala)
      .via(EncoderFlow.builder.withLimiter(limiter).flatTriples(jellyOpt).flow)

  override def quadSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    Source.fromIterator(() => AsyncParser.asyncParseQuads(is, Lang.NQ, "").asScala)
      .via(EncoderFlow.builder.withLimiter(limiter).flatQuads(jellyOpt).flow)

  override def graphSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    val ds = RDFParser.source(is)
      .lang(Lang.NQ)
      .toDatasetGraph
    RdfSource.builder.datasetAsGraphs(ds).source
      .via(EncoderFlow.builder.withLimiter(limiter).namedGraphs(jellyOpt).flow)

  override def tripleSink(os: OutputStream)(using ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeTriples.asFlatTripleStream)
      // buffer the triples to avoid OOMs and keep some perf
      .grouped(32)
      .toMat(Sink.foreach(triples => RDFDataMgr.writeTriples(os, triples.iterator.asJava)))(Keep.right)

  override def quadSink(os: OutputStream)(using ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeQuads.asFlatQuadStream)
      .grouped(32)
      .toMat(Sink.foreach(quads => RDFDataMgr.writeQuads(os, quads.iterator.asJava)))(Keep.right)

  override def graphSink(os: OutputStream)(using ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeGraphs.asDatasetStreamOfQuads)
      .toMat(Sink.foreach(quads => RDFDataMgr.writeQuads(os, quads.iterator.asJava)))(Keep.right)
