package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.jena.*
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.stream.*
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.pekko.stream.Materializer

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class JenaReactiveSerDes(implicit mat: Materializer) extends NativeSerDes[Model, Dataset]:

  val name = "Reactive writes (Apache Jena)"

  override def readTriplesW3C(is: InputStream) = JenaSerDes.readTriplesW3C(is)

  def readQuadsW3C(is: InputStream): Dataset = JenaSerDes.readQuadsW3C(is)

  def readQuadsJelly(is: InputStream): Dataset = JenaSerDes.readQuadsJelly(is)

  def readTriplesJelly(is: InputStream): Model = JenaSerDes.readTriplesJelly(is)

  def writeQuadsJelly
  (os: OutputStream, dataset: Dataset, opt: RdfStreamOptions, frameSize: Int): Unit =
    val f = EncoderSource.fromDatasetAsQuads(dataset, ByteSizeLimiter(32_000), opt)
      (jenaIterableAdapter, jenaConverterFactory)
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)

  def writeTriplesJelly
  (os: OutputStream, model: Model, opt: RdfStreamOptions, frameSize: Int): Unit =
    val f = EncoderSource.fromGraph(model, ByteSizeLimiter(32_000), opt)
      (jenaIterableAdapter, jenaConverterFactory)
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)
