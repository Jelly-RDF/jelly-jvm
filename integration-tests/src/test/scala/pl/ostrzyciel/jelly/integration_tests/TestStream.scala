package pl.ostrzyciel.jelly.integration_tests

import akka.{Done, NotUsed}
import akka.stream.scaladsl.*
import pl.ostrzyciel.jelly.core.proto.{RdfStreamFrame, RdfStreamOptions}
import pl.ostrzyciel.jelly.stream.EncoderFlow

import java.io.{InputStream, OutputStream}
import scala.concurrent.{ExecutionContext, Future}

trait TestStream:
  def tripleSource(is: InputStream, streamOpt: EncoderFlow.Options, jellyOpt: RdfStreamOptions):
  Source[RdfStreamFrame, NotUsed]

  def tripleSink(os: OutputStream)(implicit ec: ExecutionContext): Sink[RdfStreamFrame, Future[Done]]
