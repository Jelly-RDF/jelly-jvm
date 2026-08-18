package eu.neverblink.jelly.convert.jena.sparql

import eu.neverblink.jelly.convert.jena.traits.JenaTest
import eu.neverblink.jelly.core.sparql.JellySparqlConstants
import org.apache.jena.fuseki.DEF
import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.query.DatasetFactory
import org.apache.jena.sparql.core.Var
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, StringReader}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.jdk.CollectionConverters.*

/** Tests that Fuseki serves Jelly-SPARQL when the client asks for it in the Accept header.
  */
class JellySparqlFusekiSpec extends AnyWordSpec, Matchers, BeforeAndAfterAll, JenaTest:

  private val jellyMediaRange = JellySparqlFusekiLifecycle.JELLY_SPARQL_MEDIA_RANGE
  private val jellyContentType = JellySparqlConstants.JELLY_SPARQL_CONTENT_TYPE

  private var server: FusekiServer = null

  override def beforeAll(): Unit =
    val dataset = DatasetFactory.create()
    dataset
      .getDefaultModel
      .read(
        StringReader("<https://test.org/s> <https://test.org/p> \"hello\" ."),
        null,
        "N-TRIPLES",
      )
    // Port 0 – let the OS pick a free port, so that the test doesn't clash with anything.
    server = FusekiServer.create().port(0).loopback(true).add("/ds", dataset).build().start()

  override def afterAll(): Unit =
    if server != null then server.stop()

  // Time out rather than wait forever – a test that hangs blocks the whole build.
  private val client =
    HttpClient.newBuilder.connectTimeout(Duration.ofSeconds(30)).build()

  private def query(queryString: String, accept: String): HttpResponse[Array[Byte]] =
    val url = s"http://127.0.0.1:${server.getPort}/ds?query=" +
      URLEncoder.encode(queryString, StandardCharsets.UTF_8)
    val request = HttpRequest
      .newBuilder(URI.create(url))
      .header("Accept", accept)
      .timeout(Duration.ofSeconds(40))
      .build()
    client.send(request, HttpResponse.BodyHandlers.ofByteArray())

  private def contentTypeOf(response: HttpResponse[Array[Byte]]): String =
    response.headers.firstValue("Content-Type").orElse("")

  private def reader = RowSetReaderJelly(
    RowSetReaderJelly.Options(),
    JenaSparqlConverterFactory.getInstance(),
  )

  "JellySparqlFusekiLifecycle" should {
    "initialize after JellySparqlSubsystemLifecycle" in {
      JellySparqlFusekiLifecycle().level() should be > JellySparqlSubsystemLifecycle().level()
    }

    "use the correct content type for Jelly-SPARQL" in {
      jellyMediaRange.getContentTypeStr should be("application/x-jelly-sparql")
    }

    "register the content type in Fuseki's result set offer lists" in {
      DEF.rsOfferTable.entries.asScala should contain(jellyMediaRange)
      DEF.rsOfferBoolean.entries.asScala should contain(jellyMediaRange)
    }

    "not register the content type twice" in {
      val sizesBefore = (DEF.rsOfferTable.entries.size, DEF.rsOfferBoolean.entries.size)
      JellySparqlFusekiLifecycle().start()
      (DEF.rsOfferTable.entries.size, DEF.rsOfferBoolean.entries.size) should be(sizesBefore)
    }
  }

  "Fuseki with Jelly-SPARQL registered" should {
    "answer a SELECT query with Jelly-SPARQL" in {
      val response = query("SELECT * WHERE { ?s ?p ?o }", jellyContentType)
      response.statusCode should be(200)
      contentTypeOf(response) should startWith(jellyContentType)

      val rowSet = reader.read(ByteArrayInputStream(response.body), null)
      rowSet.getResultVars.asScala.map(_.getVarName) should contain theSameElementsAs Seq(
        "s",
        "p",
        "o",
      )
      val rows = rowSet.asScala.toSeq
      rows should have size 1
      rows.head.get(Var.alloc("o")).getLiteralLexicalForm should be("hello")
    }

    "answer an ASK query with Jelly-SPARQL" in {
      val response = query("ASK { ?s ?p ?o }", jellyContentType)
      response.statusCode should be(200)
      contentTypeOf(response) should startWith(jellyContentType)

      val result = reader.readAny(ByteArrayInputStream(response.body), null)
      result.isBoolean should be(true)
      result.booleanResult.booleanValue should be(true)
    }

    "not change the response format for clients that don't ask for Jelly-SPARQL" in {
      for accept <- Seq("*/*", "application/sparql-results+json", "application/sparql-results+xml")
      do
        val response = query("SELECT * WHERE { ?s ?p ?o }", accept)
        response.statusCode should be(200)
        contentTypeOf(response) should not startWith jellyContentType
    }
  }
