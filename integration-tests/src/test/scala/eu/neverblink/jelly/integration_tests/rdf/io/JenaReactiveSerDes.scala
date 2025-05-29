package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.jena.{JenaAdapters, JenaConverterFactory}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.pekko.stream.{ByteSizeLimiter, DecoderFlow, EncoderFlow, JellyIo, RdfSource}
import org.apache.jena.graph.Triple
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.Model
import org.apache.jena.sparql.core.Quad
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}

import java.io.{File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import scala.concurrent.Await
import scala.concurrent.duration.*

class JenaReactiveSerDes(implicit mat: Materializer) extends NativeSerDes[Model, Dataset], ProtocolSerDes[Triple, Quad]:
  given JenaConverterFactory = JenaConverterFactory.getInstance()

  given JenaAdapters.DATASET_ADAPTER.type = JenaAdapters.DATASET_ADAPTER
  given JenaAdapters.MODEL_ADAPTER.type = JenaAdapters.MODEL_ADAPTER

  val name = "Reactive writes (Apache Jena)"

  override def readTriplesW3C(is: InputStream): Model = JenaSerDes.readTriplesW3C(is)

  override def readQuadsW3C(is: InputStream): Dataset = JenaSerDes.readQuadsW3C(is)

  override def readTriplesW3C(files: Seq[File]): Seq[Triple] = JenaStreamSerDes.readTriplesW3C(files)

  override def readQuadsW3C(files: Seq[File]): Seq[Quad] = JenaStreamSerDes.readQuadsW3C(files)

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Dataset =
    JenaSerDes.readQuadsJelly(is, supportedOptions)

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Model =
    JenaSerDes.readTriplesJelly(is, supportedOptions)

  private def read(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Triple | Quad] =
    val f = JellyIo.fromIoStream(is)
      .via(DecoderFlow.decodeAny.asFlatStream(supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)))
      .runWith(Sink.seq)
    Await.result(f, 10.seconds)

  override def readTriplesJelly(file: File, supportedOptions: Option[RdfStreamOptions]): Seq[Triple] =
    val fileIs = new FileInputStream(file)
    try read(fileIs, supportedOptions).collect { case t: Triple => t }
    finally fileIs.close()

  override def readQuadsOrGraphsJelly(file: File, supportedOptions: Option[RdfStreamOptions]): Seq[Quad] =
    val fileIs = new FileInputStream(file)
    try read(fileIs, supportedOptions).collect { case q: Quad => q }
    finally fileIs.close()

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


  override def writeTriplesJelly(file: File, triples: Seq[Triple], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val fileOs = new FileOutputStream(file)
    val f = Source.fromIterator(() => triples.iterator)
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(32_000))
        .flatTriples(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
        .flow
      )
      .runWith(JellyIo.toIoStream(fileOs))
    Await.ready(f, 10.seconds)
    fileOs.close()

  override def writeQuadsJelly(file: File, quads: Seq[Quad], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val fileOs = new FileOutputStream(file)
    val f = Source.fromIterator(() => quads.iterator)
      .via(EncoderFlow.builder
        .withLimiter(ByteSizeLimiter(32_000))
        .flatQuads(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
        .flow
      )
      .runWith(JellyIo.toIoStream(fileOs))
    Await.ready(f, 10.seconds)
    fileOs.close()
