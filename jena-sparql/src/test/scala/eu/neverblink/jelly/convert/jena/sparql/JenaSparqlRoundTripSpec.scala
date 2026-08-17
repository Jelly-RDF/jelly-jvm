package eu.neverblink.jelly.convert.jena.sparql

import eu.neverblink.jelly.core.RdfProtoSerializationError
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.query.{QueryExecutionFactory, QueryFactory, ResultSet}
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.resultset.{ResultSetReaderRegistry, ResultSetWriterRegistry}
import org.apache.jena.riot.rowset.{RowSetReaderRegistry, RowSetWriterRegistry}
import org.apache.jena.riot.{RDFLanguages, ResultSetMgr, RiotException}
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.BindingFactory
import org.apache.jena.sparql.exec.{RowSet, RowSetStream}
import org.apache.jena.sys.JenaSystem
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.jdk.CollectionConverters.*

class JenaSparqlRoundTripSpec extends AnyWordSpec, Matchers:
  JenaSystem.init()

  private val exNs = "https://test.org/example#"

  private def iri(name: String): Node = NodeFactory.createURI(exNs + name)

  private def rowSetOf(vars: Seq[String], rows: Seq[Seq[Node | Null]]): RowSet =
    val jVars = vars.map(Var.alloc)
    val bindings = rows.map { row =>
      val builder = BindingFactory.builder()
      for (v, n) <- jVars.zip(row) if n != null do builder.add(v, n.asInstanceOf[Node])
      builder.build()
    }
    RowSetStream.create(jVars.asJava, bindings.iterator.asJava)

  private def materialize(rowSet: RowSet): (Seq[String], Seq[Map[String, Node]]) =
    val vars = rowSet.getResultVars.asScala.map(_.getVarName).toSeq
    val rows = rowSet.asScala
      .map { binding =>
        vars.flatMap(v => Option(binding.get(Var.alloc(v))).map(v -> _)).toMap
      }
      .toSeq
    (vars, rows)

  private def roundTrip(
      vars: Seq[String],
      rows: Seq[Seq[Node | Null]],
      options: RowSetWriterJelly.Options = RowSetWriterJelly.Options(),
  ): (Seq[String], Seq[Map[String, Node]]) =
    val out = ByteArrayOutputStream()
    val writer = RowSetWriterJelly(options, JenaSparqlConverterFactory.getInstance())
    writer.write(out, rowSetOf(vars, rows), null)
    val reader =
      RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())
    materialize(reader.read(ByteArrayInputStream(out.toByteArray), null))

  private def expected(vars: Seq[String], rows: Seq[Seq[Node | Null]]): Seq[Map[String, Node]] =
    rows.map(row =>
      vars.zip(row).collect { case (v, n) if n != null => v -> n.asInstanceOf[Node] }.toMap,
    )

  "Jelly-SPARQL for Jena" should {
    "be registered automatically via the Jena subsystem lifecycle" in {
      RDFLanguages.isRegistered(JellySparqlLanguage.JELLY_SPARQL) shouldBe true
      RowSetReaderRegistry.isRegistered(JellySparqlLanguage.JELLY_SPARQL) shouldBe true
      RowSetWriterRegistry.isRegistered(JellySparqlLanguage.JELLY_SPARQL) shouldBe true
      ResultSetReaderRegistry.isRegistered(JellySparqlLanguage.JELLY_SPARQL) shouldBe true
      ResultSetWriterRegistry.isRegistered(JellySparqlLanguage.JELLY_SPARQL) shouldBe true
    }

    "round-trip a result set with mixed values" in {
      val vars = Seq("s", "label", "count")
      val rows = Seq(
        Seq[Node | Null](iri("a"), NodeFactory.createLiteralString("first"), null),
        Seq[Node | Null](
          iri("b"),
          NodeFactory.createLiteralLang("deuxième", "fr"),
          NodeFactory.createLiteralDT("42", XSDDatatype.XSDinteger),
        ),
        Seq[Node | Null](NodeFactory.createBlankNode("bn1"), null, null),
      )
      val (gotVars, gotRows) = roundTrip(vars, rows)
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip across multiple frames" in {
      val vars = Seq("x")
      val rows = (1 to 25).map(i => Seq[Node | Null](iri(s"node$i")))
      val (gotVars, gotRows) =
        roundTrip(
          vars,
          rows,
          RowSetWriterJelly.Options(
            eu.neverblink.jelly.core.sparql.JellySparqlOptions.SMALL,
            // One variable, so 4 values per frame is 4 rows per frame
            4,
            true,
          ),
        )
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip in the non-delimited form" in {
      val vars = Seq("x", "y")
      val rows = Seq(
        Seq[Node | Null](iri("a"), iri("b")),
        Seq[Node | Null](iri("a"), null),
      )
      val (gotVars, gotRows) = roundTrip(
        vars,
        rows,
        RowSetWriterJelly.Options(
          eu.neverblink.jelly.core.sparql.JellySparqlOptions.SMALL,
          4,
          false,
        ),
      )
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "round-trip a result set that outgrows the lookup tables" in {
      // Regression: the writer asks for frames of a given value budget, but the lookup tables of a
      // frame may fill up first. That used to corrupt the encoder mid-frame; now the writer flushes
      // and carries on. 400 distinct IRIs against SMALL, whose name table holds 256.
      val vars = Seq("x")
      val rows = (1 to 400).map(i => Seq[Node | Null](iri(s"node$i")))
      val (gotVars, gotRows) = roundTrip(
        vars,
        rows,
        RowSetWriterJelly.Options(
          eu.neverblink.jelly.core.sparql.JellySparqlOptions.SMALL,
          // Far more values than the SMALL name table holds, so the tables end the frames
          1_000_000,
          true,
        ),
      )
      gotVars shouldBe vars
      gotRows shouldBe expected(vars, rows)
    }

    "refuse a non-delimited result set that outgrows the lookup tables" in {
      // A single frame cannot be flushed early, so there is nothing to do but say so
      val rows = (1 to 400).map(i => Seq[Node | Null](iri(s"node$i")))
      val e = intercept[RdfProtoSerializationError] {
        roundTrip(
          Seq("x"),
          rows,
          RowSetWriterJelly.Options(
            eu.neverblink.jelly.core.sparql.JellySparqlOptions.SMALL,
            1_000_000,
            false,
          ),
        )
      }
      e.getMessage should include("too large to be written as a single non-delimited frame")
    }

    "round-trip an empty result set" in {
      val (gotVars, gotRows) = roundTrip(Seq("a", "b"), Seq.empty)
      gotVars shouldBe Seq("a", "b")
      gotRows shouldBe empty
    }

    "round-trip the results of an actual query via ResultSetMgr" in {
      val model = ModelFactory.createDefaultModel()
      val p = model.createProperty(exNs, "p")
      val label = model.createProperty(exNs, "label")
      for i <- 1 to 10 do
        val subject = model.createResource(exNs + s"subject$i")
        model.add(subject, p, model.createResource(exNs + s"object${i % 3}"))
        if i % 2 == 0 then model.add(subject, label, model.createLiteral(s"label $i"))

      val query = QueryFactory.create(
        s"SELECT ?s ?o ?l WHERE { ?s <${p.getURI}> ?o . OPTIONAL { ?s <${label.getURI}> ?l } } ORDER BY ?s",
      )
      val execution = QueryExecutionFactory.create(query, model)
      val results = execution.execSelect()

      val out = ByteArrayOutputStream()
      ResultSetMgr.write(out, results, JellySparqlLanguage.JELLY_SPARQL)
      execution.close()

      val readBack: ResultSet =
        ResultSetMgr.read(ByteArrayInputStream(out.toByteArray), JellySparqlLanguage.JELLY_SPARQL)
      val (gotVars, gotRows) = materialize(RowSet.adapt(readBack))
      gotVars shouldBe Seq("s", "o", "l")
      gotRows.size shouldBe 10

      // Re-run the query and compare directly
      val execution2 = QueryExecutionFactory.create(query, model)
      val (expVars, expRows) = materialize(RowSet.adapt(execution2.execSelect()))
      execution2.close()
      gotVars shouldBe expVars
      gotRows shouldBe expRows
    }

    "round-trip a boolean (ASK) result" in {
      for value <- Seq(true, false) do
        val out = ByteArrayOutputStream()
        val writer =
          RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
        writer.write(out, value, null)
        val reader =
          RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())
        val result = reader.readAny(ByteArrayInputStream(out.toByteArray), null)
        result.isBoolean shouldBe true
        result.booleanResult() shouldBe value
    }

    "refuse to read a boolean (ASK) result as a RowSet" in {
      val out = ByteArrayOutputStream()
      val writer =
        RowSetWriterJelly(RowSetWriterJelly.Options(), JenaSparqlConverterFactory.getInstance())
      writer.write(out, true, null)
      val reader =
        RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())
      val e = intercept[RiotException] {
        reader.read(ByteArrayInputStream(out.toByteArray), null)
      }
      e.getMessage should include("boolean")
    }
  }
