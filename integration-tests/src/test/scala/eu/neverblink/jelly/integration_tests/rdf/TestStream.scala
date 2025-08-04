package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.pekko.stream.SizeLimiter
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

  def tripleSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def quadSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def graphSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def supportsRdfStar: Boolean = true
  
  def supportsRdf12: Boolean = false