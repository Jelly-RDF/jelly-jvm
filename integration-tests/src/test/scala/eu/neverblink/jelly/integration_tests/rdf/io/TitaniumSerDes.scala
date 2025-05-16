package eu.neverblink.jelly.integration_tests.rdf.io

import com.apicatalog.rdf.nquads.NQuadsReader
import com.apicatalog.rdf.{RdfDataset, RdfDatasetSupplier}
import eu.neverblink.jelly.convert.titanium.{TitaniumJellyReader, TitaniumJellyWriter}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.rdf.helpers.TitaniumDatasetEmitter
import eu.neverblink.jelly.integration_tests.util.Measure

import java.io.{InputStream, InputStreamReader, OutputStream}

given mTitaniumDataset: Measure[RdfDataset] = (ds: RdfDataset) => ds.size

object TitaniumSerDes extends NativeSerDes[RdfDataset, RdfDataset]:

  override def name: String = "Titanium"

  override def supportsRdfStar: Boolean = false

  override def supportsGeneralizedStatements: Boolean = false

  override def readTriplesW3C(is: InputStream): RdfDataset =
    val reader = NQuadsReader(InputStreamReader(is))
    val ds = RdfDatasetSupplier()
    reader.provide(ds)
    ds.get()

  override def readQuadsW3C(is: InputStream): RdfDataset = readTriplesW3C(is)

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
