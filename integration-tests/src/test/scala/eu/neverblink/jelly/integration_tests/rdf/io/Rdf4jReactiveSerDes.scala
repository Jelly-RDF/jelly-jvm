package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.pekko.stream.*
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import org.eclipse.rdf4j.model.{Statement, Value}

import java.io.*
import scala.concurrent.Await
import scala.concurrent.duration.*

class Rdf4jReactiveSerDes(using Materializer)
    extends NativeSerDes[Seq[Statement], Seq[Statement]],
      ProtocolSerDes[Value, Statement, Statement]:
  given Rdf4jConverterFactory = Rdf4jConverterFactory.getInstance()

  override def name: String = "Reactive (RDF4J)"

  override def supportsRdfStar(physicalStreamType: PhysicalStreamType): Boolean =
    physicalStreamType match
      case PhysicalStreamType.TRIPLES => true
      case _ => false

  override def supportsGeneralizedStatements: Boolean = false

  override def readTriplesW3C(is: InputStream): Seq[Statement] = Rdf4jSerDes.readTriplesW3C(is)

  override def readTriplesW3C(streams: Seq[File]): Seq[Statement] =
    Rdf4jSerDes.readTriplesW3C(streams)

  override def readQuadsW3C(is: InputStream): Seq[Statement] = Rdf4jSerDes.readQuadsW3C(is)

  override def readQuadsW3C(files: Seq[File]): Seq[Statement] = Rdf4jSerDes.readQuadsW3C(files)

  private def read(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Statement] =
    val f = JellyIo.fromIoStream(is)
      .via(
        DecoderFlow.decodeAny.asFlatStream(
          supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS),
        ),
      )
      .runWith(Sink.seq)
    Await.result(f, 10.seconds)

  override def readTriplesJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    read(is, supportedOptions)

  override def readTriplesJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    val fileIs = FileInputStream(file)
    try read(fileIs, supportedOptions)
    finally fileIs.close()

  override def readQuadsJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    read(is, supportedOptions)

  override def readQuadsOrGraphsJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[Statement] =
    val fileIs = FileInputStream(file)
    try read(fileIs, supportedOptions)
    finally fileIs.close()

  override def writeTriplesJelly(
      os: OutputStream,
      model: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val f = Source.fromIterator(() => model.iterator)
      .via(
        EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(frameSize))
          .flatTriples(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
          .flow,
      )
      .runWith(JellyIo.toIoStream(os))
    // Use Await.result to rethrow any exceptions that occur during the stream processing
    Await.result(f, 10.seconds)

  override def writeTriplesJelly(
      file: File,
      triples: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val fileOs = new FileOutputStream(file)
    val f = Source.fromIterator(() => triples.iterator)
      .via(
        EncoderFlow.builder
          .withLimiter(StreamRowCountLimiter(frameSize))
          .flatTriples(opt.getOrElse(JellyOptions.SMALL_ALL_FEATURES))
          .flow,
      )
      .runWith(JellyIo.toIoStream(fileOs))
    Await.result(f, 10.seconds)
    fileOs.close()

  override def writeQuadsJelly(
      os: OutputStream,
      dataset: Seq[Statement],
      maybeOptions: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val options = maybeOptions.getOrElse(JellyOptions.SMALL_ALL_FEATURES)
    val f = if options.getPhysicalType == PhysicalStreamType.QUADS then {
      Source.fromIterator(() => dataset.iterator)
        .via(
          EncoderFlow.builder
            .withLimiter(StreamRowCountLimiter(frameSize))
            .flatQuads(options).flow,
        )
        .runWith(JellyIo.toIoStream(os))
    } else {
      Source.fromIterator(() => dataset.iterator)
        .via(
          EncoderFlow.builder
            .withLimiter(StreamRowCountLimiter(frameSize))
            .flatGraphs(options).flow,
        )
        .runWith(JellyIo.toIoStream(os))
    }
    Await.result(f, 10.seconds)

  override def writeQuadsJelly(
      file: File,
      quads: Seq[Statement],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val fileOs = new FileOutputStream(file)
    writeQuadsJelly(fileOs, quads, opt, frameSize)
    fileOs.close()

  override def isBlank(node: Value): Boolean = Rdf4jSerDes.isBlank(node)

  override def getBlankNodeLabel(node: Value): String = Rdf4jSerDes.getBlankNodeLabel(node)

  override def isNodeTriple(node: Value): Boolean = Rdf4jSerDes.isNodeTriple(node)

  override def asNodeTriple(node: Value): Statement = Rdf4jSerDes.asNodeTriple(node)

  override def iterateTerms(statement: Statement): Seq[Value] = Rdf4jSerDes.iterateTerms(statement)
