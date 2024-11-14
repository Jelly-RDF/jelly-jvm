package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions

import java.io.{InputStream, OutputStream}

/**
 * A thing that can measure how many statements a model or dataset has.
 * @tparam T type of the thing to measure
 */
trait Measure[T]:
  def size(x: T): Long

trait NativeSerDes[TModel : Measure, TDataset : Measure]:
  def name: String
  def readTriplesW3C(is: InputStream): TModel
  def readQuadsW3C(is: InputStream): TDataset
  def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TModel
  def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): TDataset
  def writeTriplesJelly(os: OutputStream, model: TModel, opt: Option[RdfStreamOptions], frameSize: Int): Unit
  def writeQuadsJelly(os: OutputStream, dataset: TDataset, opt: Option[RdfStreamOptions], frameSize: Int): Unit
