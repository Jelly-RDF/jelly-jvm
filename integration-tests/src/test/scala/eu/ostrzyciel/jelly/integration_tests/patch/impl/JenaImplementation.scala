package eu.ostrzyciel.jelly.integration_tests.patch.impl

import eu.ostrzyciel.jelly.convert.jena.patch.*
import eu.ostrzyciel.jelly.core.patch.JellyPatchOptions
import eu.ostrzyciel.jelly.core.proto.v1.patch.RdfPatchOptions
import eu.ostrzyciel.jelly.integration_tests.patch.traits.*
import eu.ostrzyciel.jelly.integration_tests.util.TestComparable
import org.apache.jena.rdfpatch.text.RDFPatchReaderText
import org.scalatest.matchers.should.Matchers.*

import java.io.{InputStream, OutputStream}
import scala.annotation.experimental

given TestComparable[JenaChangesCollector] = new TestComparable[JenaChangesCollector]:
  override def compare(a: JenaChangesCollector, e: JenaChangesCollector): Unit =
    a.size should be (e.size)
    for ((ar, er), i) <- a.getChanges.zip(e.getChanges).zipWithIndex do
      withClue("at index $i") {
        ar should be (er)
      }
  override def size(a: JenaChangesCollector): Long = a.size


@experimental
object JenaImplementation extends RdfPatchImplementation[JenaChangesCollector]:

  override def name: String = "Jena"

  override def readRdf(in: InputStream): JenaChangesCollector =
    val collector = JenaChangesCollector()
    RDFPatchReaderText(in).apply(collector)
    collector

  override def readJelly(in: InputStream, supportedOptions: Option[RdfPatchOptions]): JenaChangesCollector =
    val collector = JenaChangesCollector()
    JellyPatchOps.read(in, collector, RdfPatchReaderJelly.Options(
      supportedOptions.getOrElse(JellyPatchOptions.defaultSupportedOptions)
    ))
    collector

  override def writeJelly(
    out: OutputStream, patch: JenaChangesCollector, options: Option[RdfPatchOptions], frameSize: Int
  ): Unit =
    val w = JellyPatchOps.writer(out, RdfPatchWriterJelly.Options(
      options.getOrElse(JellyPatchOptions.defaultSupportedOptions),
      frameSize = frameSize
    ))
    patch.replay(w)

  override def supportsGeneralizedStatements: Boolean = true

  override def supportsRdfStar: Boolean = true
