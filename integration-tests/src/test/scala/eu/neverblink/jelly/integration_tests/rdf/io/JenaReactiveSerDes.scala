package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.jena.{JenaAdapters, JenaConverterFactory}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.pekko.stream.{ByteSizeLimiter, EncoderFlow, JellyIo, RdfSource}
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.pekko.stream.Materializer

import java.io.{InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class JenaReactiveSerDes(implicit mat: Materializer) extends NativeSerDes[Model, Dataset]:
  given JenaConverterFactory = JenaConverterFactory.getInstance()

  given JenaAdapters.DATASET_ADAPTER.type = JenaAdapters.DATASET_ADAPTER
  given JenaAdapters.MODEL_ADAPTER.type = JenaAdapters.MODEL_ADAPTER

  val name = "Reactive writes (Apache Jena)"

  override def readTriplesW3C(is: InputStream) = JenaSerDes.readTriplesW3C(is)

  override def readQuadsW3C(is: InputStream): Dataset = JenaSerDes.readQuadsW3C(is)

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Dataset =
    JenaSerDes.readQuadsJelly(is, supportedOptions)

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Model =
    JenaSerDes.readTriplesJelly(is, supportedOptions)

  override def writeQuadsJelly
  (os: OutputStream, dataset: Dataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val f = RdfSource.builder().datasetAsQuads(dataset).source
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(32_000))
        .flatQuads(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
        .flow
      )
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)

  override def writeTriplesJelly
  (os: OutputStream, model: Model, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val f = RdfSource.builder().graphAsTriples(model).source
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(32_000))
        .flatTriples(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
        .flow
      )
      .runWith(JellyIo.toIoStream(os))
    Await.ready(f, 10.seconds)
