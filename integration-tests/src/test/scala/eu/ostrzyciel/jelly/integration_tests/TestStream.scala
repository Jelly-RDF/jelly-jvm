package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.ostrzyciel.jelly.stream.*
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.{Done, NotUsed}

import java.io.{InputStream, OutputStream}
import scala.concurrent.{ExecutionContext, Future}

trait TestStream:
  def tripleSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def quadSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def graphSource(is: InputStream, limiter: SizeLimiter, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def tripleSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def quadSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def graphSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]
