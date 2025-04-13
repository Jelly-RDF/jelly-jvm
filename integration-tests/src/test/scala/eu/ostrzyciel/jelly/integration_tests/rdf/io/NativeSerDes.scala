package eu.ostrzyciel.jelly.integration_tests.rdf.io

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import eu.ostrzyciel.jelly.integration_tests.util.Measure

import java.io.{InputStream, OutputStream}

trait NativeSerDes[TModel : Measure, TDataset : Measure]:
  def name: String
  def readTriplesW3C(is: InputStream): TModel
  def readQuadsW3C(is: InputStream): TDataset
  def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TModel
  def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TDataset
  def writeTriplesJelly(os: OutputStream, model: TModel, opt: Option[RdfStreamOptions], frameSize: Int): Unit
  def writeQuadsJelly(os: OutputStream, dataset: TDataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit
  def supportsRdfStar: Boolean = true
  def supportsGeneralizedStatements: Boolean = true
