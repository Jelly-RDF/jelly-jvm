package eu.neverblink.jelly.integration_tests.util

import eu.neverblink.jelly.integration_tests.rdf.io.*
import org.apache.jena.Jena
import org.scalatest.Reporter
import org.scalatest.events.*

import java.nio.file.{Files, Paths}
import java.time.{Instant, ZoneOffset}
import scala.collection.mutable
import scala.util.matching.Regex

class ConformanceReporter extends Reporter {
  lazy val jellyV: String = Option(System.getenv("JELLY_VERSION")).getOrElse("dev")
  lazy val jellyDate: String = Option(System.getenv("JELLY_DATE")).getOrElse(
    Instant.now().atZone(ZoneOffset.UTC).toLocalDate.toString,
  )

  lazy val results: Map[String, mutable.StringBuilder] = Map(
    JenaStreamSerDes.name -> initStringBuilder(JenaStreamSerDes.name),
    Rdf4jSerDes.name -> initStringBuilder(Rdf4jSerDes.name),
    "Reactive (RDF4J)" -> initStringBuilder("Reactive (RDF4J)"),
    "Reactive writes (Apache Jena)" -> initStringBuilder("Reactive writes (Apache Jena)"),
    TitaniumSerDes.name -> initStringBuilder(TitaniumSerDes.name),
  )

  def renameIntegrations(name: String): String = name match {
    case "Jena (StreamRDF)" => s"Jena ${Jena.VERSION}"
    case "Reactive (RDF4J)" => "Pekko Streams (RDF4J)"
    case "Reactive writes (Apache Jena)" => s"Pekko Streams (Jena ${Jena.VERSION})"
    case s => s
  }

  val prefixes: String =
    """PREFIX earl: <http://www.w3.org/ns/earl#>
      |PREFIX doap: <http://usefulinc.com/ns/doap#>
      |PREFIX foaf: <http://xmlns.com/foaf/0.1/>
      |PREFIX dc:   <http://purl.org/dc/terms/>
      |PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#>
      |""".stripMargin

  def metadata: String =
    s"""
       |<> foaf:primaryTopic <#impl> ;
       |   dc:issued "${Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime}"^^xsd:dateTime ;
       |   foaf:maker <#assertor> .
       |""".stripMargin

  val assertor: String =
    """
      |<#assertor> a earl:Software, earl:Assertor ;
      |  doap:name "Jelly-JVM integration test suite" ;
      |  doap:homepage <https://github.com/Jelly-RDF/jelly-jvm/tree/main/integration-tests> .
      |""".stripMargin

  def formatTestSubject(name: String): String =
    s"""
       |<#impl> a doap:Project, earl:TestSubject, earl:Software ;
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

  def formatResult(testName: String, ts: Long, outcome: String): String = {
    val date = Instant.ofEpochMilli(ts).atZone(
      ZoneOffset.UTC,
    ).toLocalDateTime.toString
    s"""
       |<#${testName.replace('/', '_')}> a earl:Assertion ;
       |  earl:assertedBy <#assertor> ;
       |  earl:subject    <#impl> ;
       |  earl:test       <https://github.com/Jelly-RDF/jelly-protobuf/tree/main/test/$testName> ;
       |  earl:mode       earl:automatic ;
       |  earl:result [
       |    a earl:TestResult ;
       |    dc:date "$date"^^xsd:dateTime ;
       |    earl:outcome earl:$outcome
       |  ] .
       |""".stripMargin
  }

  def initStringBuilder(name: String): mutable.StringBuilder = {
    val sb = mutable.StringBuilder()
    sb.append(assertor)
    sb.append(formatTestSubject(renameIntegrations(name)))
    sb
  }

  val testPattern: Regex = "^.*?erializer (.*?) when Protocol test (.*?) ".r

  override def apply(event: Event): Unit = {
    event match {
      case s: TestSucceeded =>
        val x = testPattern.findFirstMatchIn(s.testName).get
        if x.group(1) == "Jena" then println(x.group(1))
        results(x.group(1)).append(formatResult(x.group(2), s.timeStamp, "passed"))
      case s: TestCanceled =>
        val x = testPattern.findFirstMatchIn(s.testName).get
        results(x.group(1)).append(formatResult(x.group(2), s.timeStamp, "inapplicable"))
      case s: TestFailed =>
        val x = testPattern.findFirstMatchIn(s.testName).get
        results(x.group(1)).append(formatResult(x.group(2), s.timeStamp, "failed"))
      case _: SuiteCompleted =>
        results.foreach((name, sb) => {
          println(s"Writing report for $name")
          Files.createDirectories(Paths.get("integration-tests/target/reports/"))
          Files.writeString(
            Paths.get(
              s"integration-tests/target/reports/Jelly-JVM_${renameIntegrations(name)} conformance report.ttl",
            ),
            prefixes + metadata + sb.toString(),
          )
        })
      case _ =>
    }
  }
}
