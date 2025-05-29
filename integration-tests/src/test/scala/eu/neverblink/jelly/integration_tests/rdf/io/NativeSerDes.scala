package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.util.Measure

import java.io.{InputStream, OutputStream, File}

trait NativeSerDes[TModel : Measure, TDataset : Measure]:
  def name: String
  def readTriplesW3C(is: InputStream): TModel
  def readTriplesW3C(streams: Seq[File]): TModel
  def readQuadsW3C(is: InputStream): TDataset
  def readQuadsW3C(files: Seq[File]): TDataset
  def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TModel
  def readQuadsOrGraphsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TDataset
  def writeTriplesJelly(os: OutputStream, model: TModel, opt: Option[RdfStreamOptions], frameSize: Int): Unit
  def writeQuadsJelly(os: OutputStream, dataset: TDataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit
  def supportsRdfStar: Boolean = true
  def supportsGeneralizedStatements: Boolean = true
