package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.{JenaAdapters, JenaConverterFactory}
import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.core.utils.{QuadExtractor, QuadMaker, TripleExtractor, TripleMaker}
import eu.neverblink.jelly.integration_tests.util.CompatibilityUtils
import eu.neverblink.jelly.pekko.stream.{DecoderFlow, EncoderFlow, RdfSource, SizeLimiter}
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.system.AsyncParser
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.jena.sparql.core.Quad
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*
import org.apache.pekko.NotUsed
import org.apache.pekko.NotUsed
import org.apache.pekko.NotUsed
import org.apache.pekko.Done
import scala.concurrent.Future
import org.apache.pekko.Done
import scala.concurrent.Future
import org.apache.pekko.Done
import scala.concurrent.Future

case object JenaTestStream extends TestStream:
  given JenaConverterFactory = JenaConverterFactory.getInstance()

  given TripleExtractor[Node, Triple] = JenaConverterFactory.getInstance().encoderConverter()
  given QuadExtractor[Node, Quad] = JenaConverterFactory.getInstance().encoderConverter()

  given TripleMaker[Node, Triple] = JenaConverterFactory.getInstance().decoderConverter()
  given QuadMaker[Node, Quad] = JenaConverterFactory.getInstance().decoderConverter()

  given JenaAdapters.DATASET_GRAPH_ADAPTER.type = JenaAdapters.DATASET_GRAPH_ADAPTER
  given JenaAdapters.MODEL_ADAPTER.type = JenaAdapters.MODEL_ADAPTER

  override def supportsRdf12: Boolean = CompatibilityUtils.jenaVersion54OrHigher

  override def supportsRdfStar: Boolean = !CompatibilityUtils.jenaVersion54OrHigher

  override def tripleSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions): Source[RdfStreamFrame, NotUsed] =
    Source.fromIterator(() => AsyncParser.asyncParseTriples(is, Lang.NT, "").asScala)
      .via(EncoderFlow.builder.withLimiter(limiter).flatTriples(jellyOpt).flow)

  override def quadSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions): Source[RdfStreamFrame, NotUsed] =
    Source.fromIterator(() => AsyncParser.asyncParseQuads(is, Lang.NQUADS, "").asScala)
      .via(EncoderFlow.builder.withLimiter(limiter).flatQuads(jellyOpt).flow)

  override def graphSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions): Source[RdfStreamFrame, NotUsed] =
    val ds = RDFParser.source(is)
      .lang(Lang.NQ)
      .toDatasetGraph
    RdfSource.builder().datasetAsGraphs(ds).source
      .via(EncoderFlow.builder.withLimiter(limiter).namedGraphs(jellyOpt).flow)

  override def tripleSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]] =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeTriples.asFlatTripleStream)
      // buffer the triples to avoid OOMs and keep some perf
      .grouped(32)
      .toMat(Sink.foreach(triples => RDFDataMgr.writeTriples(os, triples.iterator.asJava)))(
        Keep.right,
      )

  override def quadSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]] =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeQuads.asFlatQuadStream)
      .grouped(32)
      .toMat(Sink.foreach(quads => RDFDataMgr.writeQuads(os, quads.iterator.asJava)))(Keep.right)

  override def graphSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]] =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.decodeGraphs.asDatasetStreamOfQuads)
      .toMat(Sink.foreach(quads => RDFDataMgr.writeQuads(os, quads.iterator.asJava)))(Keep.right)
