package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.system.AsyncParser
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}

import java.io.{InputStream, OutputStream}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.*

case object JenaTestStream extends TestStream:
  import eu.ostrzyciel.jelly.convert.jena.given

  override def tripleSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    Source.fromIterator(() => AsyncParser.asyncParseTriples(is, Lang.NT, "").asScala)
      .via(EncoderFlow.flatTripleStream(limiter, jellyOpt))

  override def quadSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    Source.fromIterator(() => AsyncParser.asyncParseQuads(is, Lang.NQUADS, "").asScala)
      .via(EncoderFlow.flatQuadStream(limiter, jellyOpt))

  override def graphSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions) =
    val ds = RDFParser.source(is)
      .lang(Lang.NQ)
      .toDatasetGraph
    val graphs: Iterator[(Node, Iterable[Triple])] = (
      ds.listGraphNodes().asScala.map(gNode => (gNode, Iterable.from(ds.getGraph(gNode).find.asScala))) ++
        Iterator((null, Iterable.from(ds.getDefaultGraph.find.asScala)))
    ).filter((_, g) => g.nonEmpty)
    Source.fromIterator(() => graphs)
      .via(EncoderFlow.namedGraphStream(Some(limiter), jellyOpt))

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
