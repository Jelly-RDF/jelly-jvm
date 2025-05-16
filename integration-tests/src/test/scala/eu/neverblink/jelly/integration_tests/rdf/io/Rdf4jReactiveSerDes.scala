package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.rdf4j.{Rdf4jConverterFactory, Rdf4jDatatype, Rdf4jDecoderConverter, Rdf4jEncoderConverter}
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.core.{JellyConverterFactory, JellyOptions}
import eu.neverblink.jelly.pekko.stream.{DecoderFlow, EncoderFlow, JellyIo, StreamRowCountLimiter}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.{Statement, Value}

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class Rdf4jReactiveSerDes(using Materializer) extends NativeSerDes[Seq[Statement], Seq[Statement]]:
  given Rdf4jConverterFactory = Rdf4jConverterFactory.getInstance()

  override def name: String = "Reactive (RDF4J)"

  override def supportsGeneralizedStatements: Boolean = false

  override def readTriplesW3C(is: InputStream): Seq[Statement] = Rdf4jSerDes.readTriplesW3C(is)

  override def readQuadsW3C(is: InputStream): Seq[Statement] = Rdf4jSerDes.readQuadsW3C(is)

  private def read(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] =
    val f = JellyIo.fromIoStream(is)
      .via(DecoderFlow.decodeAny.asFlatStream(supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)))
      .runWith(Sink.seq)
    Await.result(f, 10.seconds)

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] =
    read(is, supportedOptions)

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] =
    read(is, supportedOptions)

  override def writeTriplesJelly(os: OutputStream, model: Seq[Statement], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val f = Source.fromIterator(() => model.iterator)
      .via(EncoderFlow.builder
        .withLimiter(StreamRowCountLimiter(frameSize))
        .flatTriples(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
        .flow
      )
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)

  override def writeQuadsJelly(os: OutputStream, dataset: Seq[Statement], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val f = Source.fromIterator(() => dataset.iterator)
      .via(EncoderFlow.builder
        .withLimiter(StreamRowCountLimiter(frameSize))
        .flatQuads(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
        .flow
      )
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)
