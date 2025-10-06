package eu.neverblink.jelly.integration_tests.rdf.io

import com.apicatalog.rdf.model.{RdfQuad, RdfQuadSet, RdfTerm}
import com.apicatalog.rdf.nquads.NQuadsReader
import com.apicatalog.rdf.primitive.flow.{QuadAcceptor, QuadEmitter}
import eu.neverblink.jelly.convert.titanium.{TitaniumJellyReader, TitaniumJellyWriter}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.integration_tests.util.Measure

import java.io.*
import java.util
import scala.jdk.CollectionConverters.*

given mTitaniumDataset: Measure[RdfQuadSet] = (ds: RdfQuadSet) => ds.stream.count

object TitaniumSerDes
    extends NativeSerDes[RdfQuadSet, RdfQuadSet],
      ProtocolSerDes[RdfTerm, RdfQuad, RdfQuad]:

  override def name: String = "Titanium"

  override def supportsWritingTriples: Boolean = false

  override def supportsRdfStar: Boolean = false

  override def supportsRdfStar(physicalStreamType: PhysicalStreamType): Boolean = false

  override def supportsGeneralizedStatements: Boolean = false

  override def readTriplesW3C(is: InputStream): RdfQuadSet =
    val reader = NQuadsReader(InputStreamReader(is))
    val ds = SinkRdfQuadSet()
    val sink = QuadAcceptor(ds)
    reader.provide(sink)
    ds

  override def readTriplesW3C(streams: Seq[File]): Seq[RdfQuad] =
    val ds = SinkRdfQuadSet()
    val sink = QuadAcceptor(ds)
    for stream <- streams do
      val is = FileInputStream(stream)
      val reader = NQuadsReader(InputStreamReader(is))
      reader.provide(sink)
      is.close()
    ds.toSeq

  override def readQuadsW3C(is: InputStream): RdfQuadSet = readTriplesW3C(is)

  override def readQuadsW3C(files: Seq[File]): Seq[RdfQuad] = readTriplesW3C(files)

  override def readTriplesJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): RdfQuadSet =
    val reader = TitaniumJellyReader.factory(
      supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS),
    )
    val ds = SinkRdfQuadSet()
    val sink = QuadAcceptor(ds)
    reader.parseAll(sink, is)
    ds

  override def readQuadsJelly(
      is: InputStream,
      supportedOptions: Option[RdfStreamOptions],
  ): RdfQuadSet =
    readTriplesJelly(is, supportedOptions)

  override def writeTriplesJelly(
      os: OutputStream,
      model: RdfQuadSet,
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val writer = TitaniumJellyWriter.factory(
      os,
      opt.getOrElse(JellyOptions.SMALL_STRICT),
      frameSize,
    )
    QuadEmitter.create(writer).emit(model)
    writer.close()

  override def writeQuadsJelly(
      os: OutputStream,
      dataset: RdfQuadSet,
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    writeTriplesJelly(os, dataset, opt, frameSize)

  override def readTriplesJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[RdfQuad] =
    val reader = TitaniumJellyReader.factory(
      supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS),
    )
    val ds = SinkRdfQuadSet()
    val sink = QuadAcceptor(ds)
    reader.parseAll(sink, FileInputStream(file))
    ds.toSeq

  override def readQuadsOrGraphsJelly(
      file: File,
      supportedOptions: Option[RdfStreamOptions],
  ): Seq[RdfQuad] =
    readTriplesJelly(file, supportedOptions)

  override def writeTriplesJelly(
      file: File,
      triples: Seq[RdfQuad],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    val fileOs = FileOutputStream(file)
    val writer = TitaniumJellyWriter.factory(
      fileOs,
      opt.getOrElse(JellyOptions.SMALL_STRICT),
      frameSize,
    )
    val model = SinkRdfQuadSet(triples)
    QuadEmitter.create(writer).emit(model)
    writer.close()
    fileOs.close()

  override def writeQuadsJelly(
      file: File,
      quads: Seq[RdfQuad],
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit =
    writeTriplesJelly(file, quads, opt, frameSize)

  override def isBlank(node: RdfTerm): Boolean = node.isResource && node.asResource.isBlank

  override def getBlankNodeLabel(node: RdfTerm): String = node.asResource.value

  override def isNodeTriple(node: RdfTerm): Boolean = node.isInstanceOf[RdfQuad]

  override def asNodeTriple(node: RdfTerm): RdfQuad = node.asInstanceOf[RdfQuad]

  override def iterateTerms(statement: RdfQuad): Seq[RdfTerm] =
    if statement.graphName.isPresent then
      Seq(
        statement.subject,
        statement.predicate,
        statement.`object`,
        statement.graphName.get,
      )
    else Seq(statement.subject, statement.predicate, statement.`object`)

  // Utility rdf dataset collector that does not discard duplicates
  final class SinkRdfQuadSet(initial: Seq[RdfQuad] = Seq()) extends RdfQuadSet:
    private val nquads = new util.ArrayList[RdfQuad]()
    nquads.addAll(initial.asJava)

    override def add(nquad: RdfQuad): Boolean =
      nquads.add(nquad)
      true

    def toSeq: Seq[RdfQuad] =
      nquads.asScala.toSeq

    override def contains(quad: RdfQuad): Boolean =
      nquads.contains(quad)

    override def stream(): util.stream.Stream[RdfQuad] =
      nquads.stream()
