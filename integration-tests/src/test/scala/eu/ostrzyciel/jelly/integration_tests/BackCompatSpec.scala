package eu.ostrzyciel.jelly.integration_tests

import eu.ostrzyciel.jelly.convert.jena.riot.JellyLanguage
import eu.ostrzyciel.jelly.convert.jena.traits.JenaTest
import org.apache.jena.riot.{Lang, RDFDataMgr}
import org.apache.jena.sparql.core.DatasetGraphFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.*

class BackCompatSpec extends AnyWordSpec, Matchers, ScalaFutures, JenaTest:
  private val testCases = Seq(
    ("riverbench_main", "RiverBench main metadata", Seq("v1_0_0")),
    ("riverbench_nanopubs", "RiverBench nanopubs dataset metadata", Seq("v1_0_0")),
    ("weather_quads", "Jelly 1.0.0, weather data example (QUADS stream)", Seq("v1_0_0")),
  )

  for (fileName, description, versions) <- testCases do
  for version <- versions do
    s"Backward compatibility with $description" should {
      s"be maintained for Jelly version $version when parsing" in {
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
        CrossStreamingSpec.compareDatasets(jellyDg, jenaDg)
      }
    }
