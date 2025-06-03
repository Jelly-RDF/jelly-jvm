package eu.neverblink.jelly.integration_tests.rdf.io

import com.apicatalog.rdf.nquads.NQuadsReader
import com.apicatalog.rdf.{RdfDataset, RdfDatasetSupplier, RdfNQuad, RdfValue}
import eu.neverblink.jelly.convert.titanium.{TitaniumJellyReader, TitaniumJellyWriter}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.integration_tests.rdf.helpers.TitaniumDatasetEmitter
import eu.neverblink.jelly.integration_tests.util.Measure

import java.io.*
import scala.jdk.CollectionConverters.*

given mTitaniumDataset: Measure[RdfDataset] = (ds: RdfDataset) => ds.size

object TitaniumSerDes extends NativeSerDes[RdfDataset, RdfDataset], ProtocolSerDes[RdfValue, RdfNQuad, RdfNQuad]:

  override def name: String = "Titanium"

  override def supportsRdfStar: Boolean = false

  override def supportsRdfStar(physicalStreamType: PhysicalStreamType): Boolean = false

  override def supportsGeneralizedStatements: Boolean = false

  override def readTriplesW3C(is: InputStream): RdfDataset =
    val reader = NQuadsReader(InputStreamReader(is))
    val ds = RdfDatasetSupplier()
    reader.provide(ds)
    ds.get()

  override def readTriplesW3C(streams: Seq[File]): Seq[RdfNQuad] =
    val ds = RdfDatasetSupplier()
    for stream <- streams do
      val reader = NQuadsReader(InputStreamReader(FileInputStream(stream)))
      reader.provide(ds)
    ds.get().toList.asScala.toSeq

  override def readQuadsW3C(is: InputStream): RdfDataset = readTriplesW3C(is)

  override def readQuadsW3C(files: Seq[File]): Seq[RdfNQuad] = readTriplesW3C(files)

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): RdfDataset =
    val reader = TitaniumJellyReader.factory(
      supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    )
    val ds = RdfDatasetSupplier()
    reader.parseAll(ds, is)
    ds.get()

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): RdfDataset =
    readTriplesJelly(is, supportedOptions)

  override def writeTriplesJelly(os: OutputStream, model: RdfDataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val writer = TitaniumJellyWriter.factory(
      os,
      opt.getOrElse(JellyOptions.SMALL_STRICT),
      frameSize,
    )
    TitaniumDatasetEmitter.emitDatasetTo(model, writer)
    writer.close()

  override def writeQuadsJelly(os: OutputStream, dataset: RdfDataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    writeTriplesJelly(os, dataset, opt, frameSize)

  override def readTriplesJelly(file: File, supportedOptions: Option[RdfStreamOptions]): Seq[RdfNQuad] =
    val reader = TitaniumJellyReader.factory(
      supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS)
    )
    val ds = RdfDatasetSupplier()
    reader.parseAll(ds, FileInputStream(file))
    ds.get().toList.asScala.toSeq

  override def readQuadsOrGraphsJelly(file: File, supportedOptions: Option[RdfStreamOptions]): Seq[RdfNQuad] =
    readTriplesJelly(file, supportedOptions)

  override def writeTriplesJelly(file: File, triples: Seq[RdfNQuad], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val fileOs = FileOutputStream(file)
    val writer = TitaniumJellyWriter.factory(
      fileOs,
      opt.getOrElse(JellyOptions.SMALL_STRICT),
      frameSize,
    )
    TitaniumDatasetEmitter.emitDatasetTo(triples, writer)
    writer.close()
    fileOs.close()

  override def writeQuadsJelly(file: File, quads: Seq[RdfNQuad], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    writeTriplesJelly(file, quads, opt, frameSize)

  override def isBlank(node: RdfValue): Boolean = node.isBlankNode

  override def getBlankNodeLabel(node: RdfValue): String = node.getValue

  override def isNodeTriple(node: RdfValue): Boolean = node.isInstanceOf[RdfNQuad]

  override def asNodeTriple(node: RdfValue): RdfNQuad = node.asInstanceOf[RdfNQuad]

  override def iterateTerms(statement: RdfNQuad): Seq[RdfValue] =
    if statement.getGraphName.isPresent then
      Seq(statement.getSubject, statement.getPredicate, statement.getObject, statement.getGraphName.get)
    else
      Seq(statement.getSubject, statement.getPredicate, statement.getObject)
