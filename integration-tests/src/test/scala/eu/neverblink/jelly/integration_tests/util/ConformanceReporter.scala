package eu.neverblink.jelly.integration_tests.util

import eu.neverblink.jelly.integration_tests.rdf.io.*
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.{AnonId, Model, ModelFactory, Property}
import org.apache.pekko.actor.ActorSystem
import org.scalatest.Reporter
import org.scalatest.events.{Event, SuiteCompleted, TestCanceled, TestFailed, TestSucceeded}

import java.nio.file.{Files, Paths}
import java.time.{Instant, ZoneOffset}
import scala.util.matching.Regex
import scala.collection.mutable

class ConformanceReporter extends Reporter {
  lazy val jellyV = Option(System.getenv("JELLY_VERSION")).getOrElse("dev")
  lazy val jellyDate = Option(System.getenv("JELLY_DATE")).getOrElse(
    Instant.now().atZone(ZoneOffset.UTC).toLocalDate.toString,
  )

  lazy val results: Map[String, mutable.StringBuilder] = Map(
    JenaSerDes.name -> initStringBuilder(JenaSerDes.name),
    Rdf4jSerDes.name -> initStringBuilder(Rdf4jSerDes.name),
    "Reactive (RDF4J)" -> initStringBuilder("Reactive (RDF4J)"),
    "Reactive writes (Apache Jena)" -> initStringBuilder("Reactive writes (Apache Jena)"),
    TitaniumSerDes.name -> initStringBuilder(TitaniumSerDes.name),
  )

  def renameIntegrations(name: String) = name match {
    case "Reactive (RDF4J)" => "Pekko Streams (RDF4J)"
    case "Reactive writes (Apache Jena)" => "Pekko Streams (Jena)"
    case s => s
  }

  val model: Model = ModelFactory.createDefaultModel()
  val prefixes: String =
    """PREFIX earl: <http://www.w3.org/ns/earl#>
      |PREFIX doap: <http://usefulinc.com/ns/doap#>
      |PREFIX foaf: <http://xmlns.com/foaf/0.1/>
      |PREFIX dc:   <http://purl.org/dc/terms/>
      |PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
      |""".stripMargin

  val assertor: String =
    """<#assertor> a earl:Software, earl:Assertor ;
      |  doap:name "Jelly-JVM integration test suite" ;
      |  doap:homepage <https://github.com/Jelly-RDF/jelly-jvm/tree/main/integration-tests> .
      |""".stripMargin

  def formatTestSubject(name: String): String =
    s"""<#impl> a doap:Project, earl:TestSubject, earl:Software ;
       |  doap:name "Jelly-JVM ($name)" ;
       |  doap:homepage <https://w3id.org/jelly/jelly-jvm/dev/> ;
       |  doap:description "Jelly-JVM integration with $name"@en ;
       |  doap:programming-language "Java" ;
       |  doap:developer <#assertor> ;
       |  doap:release [
       |    doap:name "Jelly-JVM $jellyV" ;
       |    doap:revision "$jellyV" ;
       |    dc:created "$jellyDate"^^xsd:date
       |  ] .
       |""".stripMargin

  def initStringBuilder(name: String): mutable.StringBuilder = {
    val sb = mutable.StringBuilder()
    sb.append(prefixes)
    sb.append(assertor)
    sb.append(formatTestSubject(renameIntegrations(name)))
    sb
  }

  val testPattern: Regex = "^.*?erializer (.*?) when Protocol test (.*?) ".r

  override def apply(event: Event): Unit = {
    event match {
      case s: TestSucceeded => {
        val x = testPattern.findFirstMatchIn(s.testName).get
        results(x.group(1)).append("yay")
      }
      case s: TestCanceled => {
        val x = testPattern.findFirstMatchIn(s.testName).get
        results(x.group(1)).append("nay")
      }
      case s: TestFailed => {
        val x = testPattern.findFirstMatchIn(s.testName).get
        results(x.group(1)).append("naaay")
      }
      case _: SuiteCompleted =>
        results.foreach((name, sb) => {
          println(name)
          println(sb.toString())
        })
      case _ =>
    }
  }
}
