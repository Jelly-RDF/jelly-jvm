package eu.neverblink.jelly.integration_tests.util

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.rdf.model.{AnonId, Model, ModelFactory, Property}
import org.scalatest.Reporter
import org.scalatest.events.{Event, SuiteCompleted, TestCanceled, TestFailed, TestSucceeded}

import java.nio.file.{Files, Paths}
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex

class ConformanceReporter extends Reporter {
  val model: Model = ModelFactory.createDefaultModel()
  model.setNsPrefix("earl", "http://www.w3.org/ns/earl#")
  model.setNsPrefix("doap", "http://usefulinc.com/ns/doap#")
  model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
  model.setNsPrefix("dc", "http://purl.org/dc/terms/")
  model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")
  model.setNsPrefix("jgh", "https://github.com/Jelly-RDF/jelly-protobuf/tree/main/test/")

  val a: Property = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

  object earl:
    val assertedBy: Property = model.createProperty(model.expandPrefix("earl:assertedBy"))
    val subject: Property = model.createProperty(model.expandPrefix("earl:subject"))
    val test: Property = model.createProperty(model.expandPrefix("earl:test"))
    val mode: Property = model.createProperty(model.expandPrefix("earl:mode"))
    val result: Property = model.createProperty(model.expandPrefix("earl:result"))
    val outcome: Property = model.createProperty(model.expandPrefix("earl:outcome"))
    val passed: Property = model.createProperty(model.expandPrefix("earl:passed"))
    val inapplicable: Property = model.createProperty(model.expandPrefix("earl:inapplicable"))
    val failed: Property = model.createProperty(model.expandPrefix("earl:failed"))
    val automatic: Property = model.createProperty(model.expandPrefix("earl:automatic"))
    val TestResult: Property = model.createProperty(model.expandPrefix("earl:TestResult"))
    val Assertion: Property = model.createProperty(model.expandPrefix("earl:Assertion"))

  val date: Property = model.createProperty(model.expandPrefix("dc:date"))

  val assertor: Property = model.createProperty("#assertor")
  val impl: Property = model.createProperty("#impl")

  def addAssertion(
      softNameString: String,
      testNameString: String,
      timeStamp: Long,
      outcome: Property,
  ): Unit = {
    val name = model.createProperty(s"${softNameString.replace(' ', '_')}/$testNameString")
    model.add(name, a, earl.Assertion)
    model.add(name, earl.assertedBy, assertor)
    model.add(name, earl.subject, impl)
    model.add(name, earl.test, model.createProperty(model.expandPrefix(s"jgh:$testNameString")))
    model.add(name, earl.mode, earl.automatic)
    val bnode = model.createResource(AnonId())
    val dt = Instant.ofEpochMilli(timeStamp).atZone(ZoneOffset.UTC).toLocalDateTime.toString
    model.add(name, earl.result, bnode)
    model.add(bnode, a, earl.TestResult)
    model.add(bnode, date, model.createTypedLiteral(dt, XSDDatatype.XSDdateTime))
    model.add(bnode, earl.outcome, outcome)
  }

  val buffer: StringBuilder = new StringBuilder()

  val testPattern: Regex = "^.*?erializer (.*?) when Protocol test (.*?) ".r

  override def apply(event: Event): Unit = {
    event match {
      case s: TestSucceeded => {
        val x = testPattern.findFirstMatchIn(s.testName).get
        addAssertion(x.group(1), x.group(2), s.timeStamp, earl.passed)
      }
      case s: TestCanceled => {
        val x = testPattern.findFirstMatchIn(s.testName).get
        addAssertion(x.group(1), x.group(2), s.timeStamp, earl.inapplicable)
      }
      case s: TestFailed => {
        val x = testPattern.findFirstMatchIn(s.testName).get
        addAssertion(x.group(1), x.group(2), s.timeStamp, earl.failed)
      }
      case _: SuiteCompleted => model.write(Files.newOutputStream(Paths.get("blep.ttl")), "ttl")
      case _ =>
    }
  }
}
