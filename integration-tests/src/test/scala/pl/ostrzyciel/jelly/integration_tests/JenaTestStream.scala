package pl.ostrzyciel.jelly.integration_tests

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.jena.graph.{Graph, Node, Triple}
import org.apache.jena.riot.{Lang, RDFDataMgr, RDFParser}
import org.apache.jena.riot.system.AsyncParser
import pl.ostrzyciel.jelly.core.proto.{RdfStreamFrame, RdfStreamOptions}
import pl.ostrzyciel.jelly.stream.{DecoderFlow, EncoderFlow}

import java.io.{InputStream, OutputStream}
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*

case object JenaTestStream extends TestStream:
  import pl.ostrzyciel.jelly.convert.jena.*

  override def tripleSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    Source.fromIterator(() => AsyncParser.asyncParseTriples(is, Lang.NT, "").asScala)
      .via(EncoderFlow.fromFlatTriples(streamOpt, jellyOpt))

  override def quadSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    Source.fromIterator(() => AsyncParser.asyncParseQuads(is, Lang.NQUADS, "").asScala)
      .via(EncoderFlow.fromFlatQuads(streamOpt, jellyOpt))

  private def getGraphs(is: InputStream): Iterator[(Node, Graph)] =
    val ds = RDFParser.source(is)
      .lang(Lang.NQ)
      .toDatasetGraph
    val graphs: Iterator[(Node, Graph)] = ds.listGraphNodes().asScala.map(gNode => (gNode, ds.getGraph(gNode))) ++
      Iterator((null, ds.getDefaultGraph))
    graphs.filter((_, g) => g.size > 0)

  override def graphSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions) =
    val ds = RDFParser.source(is)
      .lang(Lang.NQ)
      .toDatasetGraph
    val graphs: Iterator[(Node, Iterable[Triple])] = (
      ds.listGraphNodes().asScala.map(gNode => (gNode, Iterable.from(ds.getGraph(gNode).find.asScala))) ++
        Iterator((null, Iterable.from(ds.getDefaultGraph.find.asScala)))
    ).filter((_, g) => g.size > 0)

    Source.fromIterator(() => graphs)
      .via(EncoderFlow.fromGraphs(streamOpt, jellyOpt))

  override def tripleSink(os: OutputStream)(implicit ec: ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.triplesToFlat)
      // buffer the triples to avoid OOMs and keep some perf
      .grouped(32)
      .toMat(Sink.foreach(triples => RDFDataMgr.writeTriples(os, triples.iterator.asJava)))(Keep.right)

  override def quadSink(os: OutputStream)(implicit ec: ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.quadsToFlat)
      .grouped(32)
      .toMat(Sink.foreach(quads => RDFDataMgr.writeQuads(os, quads.iterator.asJava)))(Keep.right)

  override def graphSink(os: OutputStream)(implicit ec: ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.graphsAsQuadsToGrouped)
      .toMat(Sink.foreach(quads => RDFDataMgr.writeQuads(os, quads.iterator.asJava)))(Keep.right)
