package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.jena.riot.{JellyFormatVariant, JellyLanguage}
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import org.apache.jena.query.{Dataset, DatasetFactory}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.*

import java.io.{InputStream, OutputStream}
import scala.jdk.CollectionConverters.*

given Measure[Model] = (m: Model) => m.size()
given Measure[Dataset] = (ds: Dataset) => ds.asDatasetGraph().find().asScala.size

object JenaSerDes extends NativeSerDes[Model, Dataset]:
  val name = "Jena"

  override def readTriplesW3C(is: InputStream): Model =
    val m = ModelFactory.createDefaultModel()
    RDFDataMgr.read(m, is, RDFLanguages.NT)
    m

  def readQuadsW3C(is: InputStream): Dataset =
    val ds = DatasetFactory.create()
    RDFDataMgr.read(ds, is, RDFLanguages.NQUADS)
    ds

  def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Dataset =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.defaultSupportedOptions))
    val ds = DatasetFactory.create()
    RDFParser.create()
      .source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(ds)
    ds

  def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Model =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.defaultSupportedOptions))
    val m = ModelFactory.createDefaultModel()
    RDFParser.create()
      .source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(m)
    m

  def writeQuadsJelly
  (os: OutputStream, dataset: Dataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    var variant = JellyFormatVariant(frameSize = frameSize)
    if opt.isDefined then
      variant = variant.copy(opt = opt.get)
    val format = new RDFFormat(JellyLanguage.JELLY, variant)
    RDFDataMgr.write(os, dataset, format)

  def writeTriplesJelly
  (os: OutputStream, model: Model, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    var variant = JellyFormatVariant(frameSize = frameSize)
    if opt.isDefined then
      variant = variant.copy(opt = opt.get)
    val format = new RDFFormat(JellyLanguage.JELLY, variant)
    RDFDataMgr.write(os, model, format)
