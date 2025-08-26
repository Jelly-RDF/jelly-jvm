package eu.neverblink.jelly.convert.neo4j

import eu.neverblink.jelly.convert.neo4j.rio.JellyBase64Format
import n10s.graphconfig.GraphConfigProcedures
import n10s.rdf.`export`.RDFExportProcedures
import n10s.rdf.delete.RDFDeleteProcedures
import n10s.rdf.load.RDFLoadProcedures
import org.eclipse.rdf4j.rio.Rio

import java.io.StringReader

class JellyPluginSpec extends Neo4jSpec:
  override val functions: Seq[Class[?]] = Seq(classOf[JellyVersion])
  override val aggregations: Seq[Class[?]] = Seq(
    classOf[JellyTripleCollector],
  )
  override val procedures: Seq[Class[?]] = Seq(
    classOf[RDFLoadProcedures],
    classOf[RDFDeleteProcedures],
    classOf[RDFExportProcedures],
    classOf[GraphConfigProcedures],
  )

  private val rbFileUri = getClass.getResource("/riverbench_main_v1_1_1.jelly").toURI.toString
  private val base64FileUri = getClass.getResource("/riverbench_nanopubs.jelly_b64").toURI.toString

  "JellyVersion" should {
    "return valid version" in {
      withSession { session =>
        val result = session.run("RETURN jelly.version() AS v")
        result.single().asMap().get("v") shouldBe "dev"
      }
    }
  }

  "Neo4j" should {
    "set up the constraint for n10s" in {
      withSession { session =>
        session.run("CREATE CONSTRAINT n10s_unique_uri FOR (r:Resource) REQUIRE r.uri IS UNIQUE")
      }
    }

    "initialize graph config" in {
      withSession { session =>
        session.run(
          """
            |CALL n10s.graphconfig.init({
            |  handleVocabUris: 'SHORTEN'
            |})
            |""".stripMargin,
        )
      }
    }
  }

  "n10s.rdf.import.fetch" should {
    "import RDF data from a Jelly file" in {
      withSession { session =>
        val result = session.run(
          s"""
             |CALL n10s.rdf.import.fetch('$rbFileUri', 'Jelly')
             |""".stripMargin,
        )

        val record = result.single().asMap()
        record.get("terminationStatus") shouldBe "OK"
        record.get("triplesLoaded") shouldBe java.lang.Long.valueOf(23)
        record.get("triplesParsed") shouldBe java.lang.Long.valueOf(23)
      }
    }

    "import RDF data from a base64 Jelly file" in {
      withSession { session =>
        val result = session.run(
          s"""
             |CALL n10s.rdf.import.fetch('$base64FileUri', 'Jelly-base64')
             |""".stripMargin,
        )

        val record = result.single().asMap()
        record.get("terminationStatus") shouldBe "OK"
        record.get("triplesLoaded") shouldBe java.lang.Long.valueOf(762)
        record.get("triplesParsed") shouldBe java.lang.Long.valueOf(762)
      }
    }
  }

  /// TODO queries
  "n10s.rdf.export.cypher" should {
    "export RDF data via Cypher" in {
      withSession { session =>
        val result = session.run(
          """
             |CALL n10s.rdf.export.cypher("MATCH (p) RETURN p")
             |yield subject, predicate, object, isLiteral, literalType, literalLang
             |return n10s.rdf.collect.jelly_base64(subject, predicate, object, isLiteral, literalType, literalLang) as rdf
             |""".stripMargin,
        )

        val record = result.single().asMap()
        val rdf = record.get("rdf").asInstanceOf[String]
        rdf should not be empty
        Rio.parse(StringReader(rdf), "", JellyBase64Format.JELLY_BASE64) should have size 573
      }
    }
  }

  "n10s.rdf.delete.fetch" should {
    "delete RDF data from a Jelly file" in {
      withSession { session =>
        val result = session.run(
          s"""
             |CALL n10s.rdf.delete.fetch('$rbFileUri', 'Jelly')
             |""".stripMargin,
        )

        val record = result.single().asMap()
        record.get("terminationStatus") shouldBe "OK"
        record.get("triplesDeleted") shouldBe java.lang.Long.valueOf(23)
      }
    }

    "delete RDF data from a base64 Jelly file" in {
      withSession { session =>
        val result = session.run(
          s"""
             |CALL n10s.rdf.delete.fetch('$base64FileUri', 'Jelly-base64')
             |""".stripMargin,
        )

        val record = result.single().asMap()
        record.get("terminationStatus") shouldBe "OK"
        record.get("triplesDeleted") shouldBe java.lang.Long.valueOf(498)
      }
    }
  }
