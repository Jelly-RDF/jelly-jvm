package eu.neverblink.jelly.integration_tests.rdf.io

import com.apicatalog.rdf.api.RdfQuadConsumer
import com.apicatalog.rdf.nquads.NQuadsReader
import eu.neverblink.jelly.convert.titanium.{TitaniumJellyReader, TitaniumJellyWriter}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.integration_tests.util.Measure

import java.io.*
import scala.collection.mutable

/** A term of a quad, in the form Titanium passes it to an RdfQuadConsumer. Datatype, language and
  * direction are only set for literals.
  */
final case class TitaniumTerm(
    value: String,
    datatype: String = null,
    language: String = null,
    direction: String = null,
)

final case class TitaniumQuad(
    subject: TitaniumTerm,
    predicate: TitaniumTerm,
    `object`: TitaniumTerm,
    graph: Option[TitaniumTerm],
)

given mTitaniumDataset: Measure[Seq[TitaniumQuad]] = (ds: Seq[TitaniumQuad]) => ds.size

object TitaniumSerDes
    extends NativeSerDes[Seq[TitaniumQuad], Seq[TitaniumQuad]],
      ProtocolSerDes[TitaniumTerm, TitaniumQuad, TitaniumQuad]:

  override def name: String = "Titanium"

  override def supportsWritingTriples: Boolean = false

  override def supportsRdfStar: Boolean = false

  override def supportsRdfStar(physicalStreamType: PhysicalStreamType): Boolean = false

  override def supportsGeneralizedStatements: Boolean = false

  override def supportsRdf12Basic: Boolean = true

  override def readTriplesW3C(is: InputStream): Seq[TitaniumQuad] =
    val reader = NQuadsReader(InputStreamReader(is))
    val sink = QuadCollector()
    reader.provide(sink)
    sink.toSeq

  override def readTriplesW3C(streams: Seq[File]): Seq[TitaniumQuad] =
    val sink = QuadCollector()
    for stream <- streams do
      val is = FileInputStream(stream)
      val reader = NQuadsReader(InputStreamReader(is))
      reader.provide(sink)
      is.close()
    sink.toSeq

  override def readQuadsW3C(is: InputStream): Seq[TitaniumQuad] = readTriplesW3C(is)

  override def readQuadsW3C(files: Seq[File]): Seq[TitaniumQuad] = readTriplesW3C(files)

  override def readTriplesJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[TitaniumQuad] =
    val reader = TitaniumJellyReader.factory(
      supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS),
    )
    val sink = QuadCollector()
    reader.parseAll(sink, is)
    sink.toSeq

  override def readQuadsJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[TitaniumQuad] =
    readTriplesJelly(is, supportedOptions)

  override def writeTriplesJelly(
      os: OutputStream,
      model: Seq[TitaniumQuad],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val writer = TitaniumJellyWriter.factory(
      os,
      opt.getOrElse(JellyOptions.SMALL_STRICT),
      frameSize,
    )
    emit(model, writer)
    writer.close()

  override def writeQuadsJelly(
      os: OutputStream,
      dataset: Seq[TitaniumQuad],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    writeTriplesJelly(os, dataset, opt, frameSize)

  override def readTriplesJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[TitaniumQuad] =
    val reader = TitaniumJellyReader.factory(
      supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS),
    )
    val sink = QuadCollector()
    reader.parseAll(sink, FileInputStream(file))
    sink.toSeq

  override def readQuadsOrGraphsJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[TitaniumQuad] =
    readTriplesJelly(file, supportedOptions)

  override def writeTriplesJelly(
      file: File,
      triples: Seq[TitaniumQuad],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val fileOs = FileOutputStream(file)
    val writer = TitaniumJellyWriter.factory(
      fileOs,
      opt.getOrElse(JellyOptions.SMALL_STRICT),
      frameSize,
    )
    emit(triples, writer)
    writer.close()
    fileOs.close()

  override def writeQuadsJelly(
      file: File,
      quads: Seq[TitaniumQuad],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    writeTriplesJelly(file, quads, opt, frameSize)

  override def isBlank(node: TitaniumTerm): Boolean =
    node.datatype == null && RdfQuadConsumer.isBlank(node.value)

  override def getBlankNodeLabel(node: TitaniumTerm): String = node.value

  // Titanium does not support RDF-star
  override def isNodeTriple(node: TitaniumTerm): Boolean = false

  override def asNodeTriple(node: TitaniumTerm): TitaniumQuad =
    throw UnsupportedOperationException("Titanium does not support RDF-star")

  override def iterateTerms(statement: TitaniumQuad): Seq[TitaniumTerm] =
    Seq(statement.subject, statement.predicate, statement.`object`) ++ statement.graph

  private def emit(quads: Seq[TitaniumQuad], consumer: RdfQuadConsumer): Unit =
    for q <- quads do
      consumer.quad(
        q.subject.value,
        q.predicate.value,
        q.`object`.value,
        q.`object`.datatype,
        q.`object`.language,
        q.`object`.direction,
        q.graph.map(_.value).orNull,
      )

  // Utility quad collector that does not discard duplicates
  final class QuadCollector extends RdfQuadConsumer:
    private val quads = mutable.ArrayBuffer[TitaniumQuad]()

    override def quad(
        subject: String,
        predicate: String,
        `object`: String,
        datatype: String,
        language: String,
        direction: String,
        graph: String,
    ): Unit =
      quads += TitaniumQuad(
        TitaniumTerm(subject),
        TitaniumTerm(predicate),
        TitaniumTerm(`object`, datatype, language, direction),
        Option(graph).map(TitaniumTerm(_)),
      )

    def toSeq: Seq[TitaniumQuad] = quads.toSeq
