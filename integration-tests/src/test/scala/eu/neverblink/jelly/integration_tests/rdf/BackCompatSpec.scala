package eu.neverblink.jelly.integration_tests.rdf

import eu.neverblink.jelly.convert.jena.riot.JellyLanguage
import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.JellyConstants
import eu.neverblink.jelly.core.proto.v1.*
import eu.neverblink.jelly.integration_tests.rdf.util.Comparisons
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.sparql.core.DatasetGraphFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.jdk.CollectionConverters.*

object BackCompatSpec:
  val descriptions = Map(
    "riverbench_main" -> "RiverBench main metadata",
    "riverbench_nanopubs" -> "RiverBench nanopubs dataset metadata",
    "weather_quads" -> "weather data example (QUADS stream)"
  )

  val versionToNumber = Map(
    "v1_0_0" -> 1,
    "v1_1_0" -> 2,
    "v1_1_1" -> 2,
  )

  lazy val testCases: Seq[(String, String, Seq[String])] =
    File(getClass.getResource("/backcompat").toURI).listFiles().toSeq
      .filter(_.getName.endsWith(".jelly"))
      .map(f => (f.getName, f.getName.split("_v").head.split('.').head))
      .groupBy(_._2)
      .map { case (name, files) =>
        val versions = files.map(f => "v" + f._1.split("_v").last.split('.').head).sorted
        (name, descriptions(name), versions)
      }
      .toSeq

class BackCompatSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  import BackCompatSpec.*

  val currentVersion = "v" + JellyConstants.PROTO_SEMANTIC_VERSION.replace(".", "_")

  for (fileName, description, versions) <- testCases do
    s"Backward compatibility with $description" should {
      s"be tested with the current version ($currentVersion)" in {
        // If this test is failing, it means that you have to update this spec :)
        // Go to util/MakeBackCompatTestCases.scala and run it. This should fix it.
        versions should contain (currentVersion)
      }

      for version <- versions do s"be maintained for Jelly version $version when parsing" in {
        val jellyDg = DatasetGraphFactory.create()
        RDFDataMgr.read(
          jellyDg,
          getClass.getResourceAsStream(s"/backcompat/${fileName}_$version.jelly"),
          JellyLanguage.JELLY
        )
        val jenaDg = DatasetGraphFactory.create()
        RDFDataMgr.read(
          jenaDg,
          getClass.getResourceAsStream(s"/backcompat/$fileName.trig"),
          Lang.TRIG
        )
        Comparisons.compareDatasets(jellyDg, jenaDg)
      }

      for version <- versions do s"work for $version, reading the correct version number from file" in {
        val is = getClass.getResourceAsStream(s"/backcompat/${fileName}_$version.jelly")
        val opt: RdfStreamOptions = RdfStreamFrame.parseDelimitedFrom(is).getRows.asScala
          .head.getOptions
        opt.getVersion should be (versionToNumber(version))
      }
    }
