package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.rdf4j.{Rdf4jConverterFactory, Rdf4jDatatype}
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.core.utils.{QuadDecoder, QuadEncoder, TripleDecoder, TripleEncoder}
import eu.neverblink.jelly.core.{JellyConverterFactory, JellyOptions}
import eu.neverblink.jelly.stream.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.{Statement, Value}

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class Rdf4jReactiveSerDes(using Materializer) extends NativeSerDes[Seq[Statement], Seq[Statement]]:
  given JellyConverterFactory[Value, Rdf4jDatatype, ?, ?] = Rdf4jConverterFactory.getInstance()

  given TripleDecoder[Value, Statement] = Rdf4jConverterFactory.getInstance().encoderConverter()
  given QuadDecoder[Value, Statement] = Rdf4jConverterFactory.getInstance().encoderConverter()

  given TripleEncoder[Value, Statement] = Rdf4jConverterFactory.getInstance().decoderConverter()
  given QuadEncoder[Value, Statement] = Rdf4jConverterFactory.getInstance().decoderConverter()

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
