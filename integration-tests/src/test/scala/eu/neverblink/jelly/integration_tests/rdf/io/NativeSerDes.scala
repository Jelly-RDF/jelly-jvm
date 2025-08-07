package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.util.Measure

import java.io.{InputStream, OutputStream}

trait NativeSerDes[TModel: Measure, TDataset: Measure]:
  def name: String
  def readTriplesW3C(is: InputStream): TModel
  def readQuadsW3C(is: InputStream): TDataset
  def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TModel
  def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TDataset
  def writeTriplesJelly(
      os: OutputStream,
      model: TModel,
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit
  def writeQuadsJelly(
      os: OutputStream,
      dataset: TDataset,
      opt: Option[RdfStreamOptions],
      frameSize: Int,
  ): Unit
  def supportsRdfStar: Boolean = true
  def supportsGeneralizedStatements: Boolean = true

  /** This is needed because Jena suddenly dropped RDF-star support in 5.4. While Jelly doesn't
    * support RDF1.2, some RDF-star test cases can be translated 1:1 to RDF1.2. This allows some
    * testing of quoted triples even with Jena 5.4+ See:
    * https://github.com/Jelly-RDF/jelly-jvm/issues/368
    */
  def supportsRdf12: Boolean = false
