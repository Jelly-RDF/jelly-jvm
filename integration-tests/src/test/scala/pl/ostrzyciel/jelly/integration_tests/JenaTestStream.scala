package pl.ostrzyciel.jelly.integration_tests

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.apache.jena.riot.{Lang, RDFDataMgr}
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

  override def tripleSink(os: OutputStream)(implicit ec: ExecutionContext) =
    Flow[RdfStreamFrame]
      .via(DecoderFlow.triplesToFlat)
      // buffer the triples to avoid OOMs and keep some perf
      .grouped(32)
      .toMat(Sink.foreach(triples => RDFDataMgr.writeTriples(os, triples.iterator.asJava)))(Keep.right)
