package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.jena.riot.{JellyFormatVariant, JellyLanguage}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.util.Measure
import org.apache.jena.query.{Dataset, DatasetFactory}
import org.apache.jena.rdf.model.{Model, ModelFactory}
import org.apache.jena.riot.*

import java.io.{InputStream, OutputStream}
import scala.jdk.CollectionConverters.*

given Measure[Model] = (m: Model) => m.size()
given Measure[Dataset] = (ds: Dataset) => ds.asDatasetGraph().find().asScala.size

object JenaSerDes extends NativeSerDes[Model, Dataset]:
  val name = "Jena"

  override def supportsRdfStar: Boolean = false

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
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS))
    val ds = DatasetFactory.create()
    RDFParser.create()
      .source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(ds)
    ds

  def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Model =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS))
    val m = ModelFactory.createDefaultModel()
    RDFParser.create()
      .source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(m)
    m

  def writeQuadsJelly
  (os: OutputStream, dataset: Dataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    var variant = JellyFormatVariant.builder.frameSize(frameSize).build()
    if opt.isDefined then
      variant = variant.withOptions(opt.get)
    val format = new RDFFormat(JellyLanguage.JELLY, variant)
    RDFDataMgr.write(os, dataset, format)

  def writeTriplesJelly
  (os: OutputStream, model: Model, opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    var variant = JellyFormatVariant.builder().frameSize(frameSize).build()
    if opt.isDefined then
      variant = variant.withOptions(opt.get)
    val format = new RDFFormat(JellyLanguage.JELLY, variant)
    RDFDataMgr.write(os, model, format)
