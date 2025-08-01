package eu.neverblink.jelly.integration_tests.patch.impl

import eu.neverblink.jelly.convert.jena.patch.*
import eu.neverblink.jelly.core.patch.JellyPatchOptions
import eu.neverblink.jelly.core.proto.v1.patch.{PatchStatementType, RdfPatchOptions}
import eu.neverblink.jelly.integration_tests.patch.traits.*
import eu.neverblink.jelly.integration_tests.util.TestComparable
import org.apache.jena.rdfpatch.text.RDFPatchReaderText
import org.scalatest.matchers.should.Matchers.*
import org.apache.jena.Jena

import java.io.{File, FileInputStream, InputStream, OutputStream}
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

given TestComparable[JenaChangesCollector] = new TestComparable[JenaChangesCollector]:
  override def compare(a: JenaChangesCollector, e: JenaChangesCollector): Unit =
    a.size should be (e.size)
    for ((ar, er), i) <- a.getChanges.asScala.zip(e.getChanges.asScala).zipWithIndex do
      withClue(f"at index $i") {
        ar should be (er)
      }
  override def size(a: JenaChangesCollector): Long = a.size


@experimental
object JenaImplementation extends RdfPatchImplementation[JenaChangesCollector]:

  override def name: String = "Jena"

  lazy val jenaVersion54OrHigher: Boolean = {
    val split = Jena.VERSION.split('.')
    split(0).toInt > 5 || split(1).toInt >= 4
  }

  override def supportsRdfStar: Boolean = !jenaVersion54OrHigher

  override def readRdf(in: InputStream, stType: PatchStatementType): JenaChangesCollector =
    val collector = JellyPatchOps.changesCollector(stType)
    RDFPatchReaderText(in).apply(collector)
    collector

  override def readRdf(files: Seq[File], stType: PatchStatementType, flat: Boolean): JenaChangesCollector =
    val collector = JellyPatchOps.changesCollector(stType)
    for filename <- files do
      val in = new FileInputStream(filename)
      RDFPatchReaderText(in).apply(collector)
      in.close()
      if !flat then collector.segment()
    collector

  override def readJelly(in: InputStream, supportedOptions: Option[RdfPatchOptions]): JenaChangesCollector =
    val collector = JellyPatchOps.changesCollector(PatchStatementType.UNSPECIFIED)
    RdfPatchReaderJelly(
      RdfPatchReaderJelly.Options(supportedOptions.getOrElse(JellyPatchOptions.DEFAULT_SUPPORTED_OPTIONS)),
      JenaPatchConverterFactory.getInstance(),
      in
    ).apply(collector)

    collector

  override def writeJelly(
    out: OutputStream, patch: JenaChangesCollector, options: Option[RdfPatchOptions], frameSize: Int
  ): Unit =
    val w = RdfPatchWriterJelly(
      RdfPatchWriterJelly.Options(options.getOrElse(RdfPatchWriterJelly.Options().patchOptions), frameSize, true),
      JenaPatchConverterFactory.getInstance(),
      out
    )
    patch.replay(w, true)

  override def supportsGeneralizedStatements: Boolean = true
