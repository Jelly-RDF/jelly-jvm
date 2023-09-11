package eu.ostrzyciel.jelly.integration_tests

import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.scaladsl.*
import eu.ostrzyciel.jelly.stream.EncoderFlow
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}

import java.io.{InputStream, OutputStream}
import scala.concurrent.{ExecutionContext, Future}

trait TestStream:
  def tripleSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def quadSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def graphSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def tripleSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def quadSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def graphSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]
