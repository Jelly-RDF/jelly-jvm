package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.pekko.stream.SizeLimiter
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.{Done, NotUsed}

import java.io.{InputStream, OutputStream}
import scala.concurrent.{ExecutionContext, Future}

trait TestStream:
  def tripleSource(
      is: InputStream,
      limiter: SizeLimiter,
      jellyOpt: RdfStreamOptions,
  ): Source[RdfStreamFrame, NotUsed]

  def quadSource(
      is: InputStream,
      limiter: SizeLimiter,
      jellyOpt: RdfStreamOptions,
  ): Source[RdfStreamFrame, NotUsed]

  def graphSource(
      is: InputStream,
      limiter: SizeLimiter,
      jellyOpt: RdfStreamOptions,
  ): Source[RdfStreamFrame, NotUsed]

  def tripleSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def quadSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def graphSink(os: OutputStream)(using ExecutionContext): Sink[RdfStreamFrame, Future[Done]]

  def supportsRdfStar: Boolean = true

  /** This is needed because Jena suddenly dropped RDF-star support in 5.4. While Jelly doesn't
    * support RDF1.2, some RDF-star test cases can be translated 1:1 to RDF1.2. This allows some
    * testing of quoted triples even with Jena 5.4+ See:
    * https://github.com/Jelly-RDF/jelly-jvm/issues/368
    */
  def supportsRdf12: Boolean = false
