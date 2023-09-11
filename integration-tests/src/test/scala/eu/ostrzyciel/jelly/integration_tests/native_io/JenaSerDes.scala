package eu.ostrzyciel.jelly.integration_tests.native_io

import eu.ostrzyciel.jelly.convert.jena.riot.{JellyFormatVariant, JellyLanguage}
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import org.apache.jena.query.{Dataset, DatasetFactory}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.{RDFDataMgr, RDFFormat, RDFLanguages}

import java.io.{InputStream, OutputStream}
import scala.jdk.CollectionConverters._

implicit val modelMeasure: Measure[Model] = (m: Model) => m.size()
implicit val datasetMeasure: Measure[Dataset] = (ds: Dataset) => ds.asDatasetGraph().find().asScala.size

object JenaSerDes extends NativeSerDes[Model, Dataset]:
  val name = "Jena"

  override def readTriplesW3C(is: InputStream) =
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, is, RDFLanguages.NT)
    m

  def readQuadsW3C(is: InputStream): Dataset =
    val ds = DatasetFactory.create()
    RDFDataMgr.read(ds, is, RDFLanguages.NQUADS)
    ds

  def readQuadsJelly(is: InputStream): org.apache.jena.query.Dataset =
    val ds = DatasetFactory.create()
    RDFDataMgr.read(ds, is, JellyLanguage.JELLY)
    ds

  def readTriplesJelly(is: InputStream): org.apache.jena.rdf.model.Model =
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, is, JellyLanguage.JELLY)
    m

  def writeQuadsJelly
  (os: OutputStream, dataset: Dataset, opt: RdfStreamOptions, frameSize: Int): Unit =
    val format = new RDFFormat(JellyLanguage.JELLY, JellyFormatVariant(opt, frameSize))
    RDFDataMgr.write(os, dataset, format)

  def writeTriplesJelly
  (os: OutputStream, model: Model, opt: RdfStreamOptions, frameSize: Int): Unit =
    val format = new RDFFormat(JellyLanguage.JELLY, JellyFormatVariant(opt, frameSize))
    RDFDataMgr.write(os, model, format)