package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.Statement

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class Rdf4jReactiveSerDes(using Materializer) extends NativeSerDes[Seq[Statement], Seq[Statement]]:
  given Rdf4jConverterFactory.type = Rdf4jConverterFactory

  override def name: String = "Reactive (RDF4J)"

  override def readTriplesW3C(is: InputStream): Seq[Statement] = Rdf4jSerDes.readTriplesW3C(is)

  override def readQuadsW3C(is: InputStream): Seq[Statement] = Rdf4jSerDes.readQuadsW3C(is)

  private def read(is: InputStream): Seq[Statement] =
    val f = JellyIo.fromIoStream(is)
      .via(DecoderFlow.decodeAny.asFlatStream)
      .runWith(Sink.seq)
    Await.result(f, 10.seconds)

  override def readTriplesJelly(is: InputStream): Seq[Statement] = read(is)

  override def readQuadsJelly(is: InputStream): Seq[Statement] = read(is)

  override def writeTriplesJelly(os: OutputStream, model: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    val f = Source.fromIterator(() => model.iterator)
      .via(EncoderFlow.flatTripleStream(StreamRowCountLimiter(frameSize), opt))
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)

  override def writeQuadsJelly(os: OutputStream, dataset: Seq[Statement], opt: RdfStreamOptions, frameSize: Int): Unit =
    val f = Source.fromIterator(() => dataset.iterator)
      .via(EncoderFlow.flatQuadStream(StreamRowCountLimiter(frameSize), opt))
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)
