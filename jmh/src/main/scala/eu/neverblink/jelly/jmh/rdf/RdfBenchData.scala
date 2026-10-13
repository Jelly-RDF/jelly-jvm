package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.jmh.FairSettings
import eu.neverblink.jelly.jmh.sparql.RiverBenchData
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.riot.system.{StreamRDFBase, StreamRDFWriter}
import org.apache.jena.riot.{Lang, RDFFormat, RDFParser}
import org.apache.jena.sparql.core.Quad
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler
import org.eclipse.rdf4j.rio.{Rio, RDFFormat as Rdf4jFormat}

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable

/** RiverBench datasets as RDF statements, for [[RdfFormatBench]].
  *
  * {{{
  * val data = RdfBenchData.load("nanopubs", 100_000)
  * }}}
  *
  * The statements are decoded from Jelly, so a term that occurs many times is often one shared
  * object, which helps writers that cache terms.
  */
object RdfBenchData:

  /** With `-Djelly.bench.input=parsed`, each library's statements come from its own N-Quads parser
    * instead of Jelly, which yields RDF terms that don't have referential equality between each
    * other.
    */
  private val parsedInput = sys.props.get("jelly.bench.input").contains("parsed")

  /** `-Djelly.bench.prefixes=false` provides the writer with no prefixes, for checking what they
    * cost.
    */
  private val withPrefixes = !sys.props.get("jelly.bench.prefixes").contains("false")

  private def prefixes(dataset: String): Seq[(String, String)] =
    if withPrefixes then RiverBenchData.prefixes(dataset) else Seq.empty

  def datasetNames: IndexedSeq[String] = RiverBenchData.datasetNames

  /** The first `rows` statements of a dataset, in each library's terms. Each library's statements
    * are only made when that library is used.
    *
    * @param size
    *   how many statements there are
    * @param quads
    *   whether the dataset is made of quads (then the default graph is
    *   `Quad.defaultGraphNodeGenerated` in Jena and a null context in RDF4J) or of triples
    * @param prefixes
    *   (prefix, namespace) pairs, see [[RiverBenchData.prefixes]]
    */
  final class Data(
      val name: String,
      val size: Int,
      val quads: Boolean,
      val prefixes: Seq[(String, String)],
      jenaRows: => IndexedSeq[Array[Node]],
      rdf4jRows: => IndexedSeq[Array[Value]],
  ):
    /** The statements as Jena triples. Only if the dataset is made of triples. */
    lazy val jenaTriples: Array[Triple] =
      require(!quads, s"'$name' is made of quads")
      jenaRows.map(r => Triple.create(r(0), r(1), r(2))).toArray

    /** The statements as Jena quads. Only if the dataset is made of quads. */
    lazy val jenaQuads: Array[Quad] =
      require(quads, s"'$name' is made of triples")
      jenaRows.map { r =>
        Quad.create(if r(3) == null then Quad.defaultGraphNodeGenerated else r(3), r(0), r(1), r(2))
      }.toArray

    lazy val rdf4j: Array[Statement] =
      val vf = SimpleValueFactory.getInstance()
      rdf4jRows.map { r =>
        // RDF4J statements cannot have triple terms as subjects, so a dataset that has them fails
        // here, with a ClassCastException
        val subject = r(0).asInstanceOf[Resource]
        val predicate = r(1).asInstanceOf[IRI]
        if r.length == 4 && r(3) != null then
          vf.createStatement(subject, predicate, r(2), r(3).asInstanceOf[Resource])
        else vf.createStatement(subject, predicate, r(2))
      }.toArray

  def load(dataset: String, rows: Int): Data =
    // Each library reads the file with its own Jelly decoder, only if it is used
    lazy val jenaRows = RiverBenchData.load[Node](
      dataset,
      rows,
      JenaConverterFactory.getInstance(),
      new Array[Node](_),
      Quad.isDefaultGraph,
    )._2
    lazy val rdf4jRows = RiverBenchData.load[Value](
      dataset,
      rows,
      Rdf4jConverterFactory.getInstance(),
      new Array[Value](_),
      // RDF4J's decoder already returns null for the default graph
      _ == null,
    )._2
    // Peek at the first statement for whether the dataset has quads
    val (quads, _) = RiverBenchData.load[Node](
      dataset,
      1,
      JenaConverterFactory.getInstance(),
      new Array[Node](_),
      Quad.isDefaultGraph,
    )
    if parsedInput then
      lazy val nquads = toNQuads(jenaRows)
      Data(
        dataset,
        rows,
        quads,
        prefixes(dataset),
        parseJena(nquads, quads),
        parseRdf4j(nquads, quads),
      )
    else Data(dataset, rows, quads, prefixes(dataset), jenaRows, rdf4jRows)

  private def toNQuads(rows: IndexedSeq[Array[Node]]): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = StreamRDFWriter.getWriterStream(out, RDFFormat.NQUADS)
    writer.start()
    for r <- rows do
      val graph = if r.length == 4 && r(3) != null then r(3) else Quad.defaultGraphNodeGenerated
      writer.quad(Quad.create(graph, r(0), r(1), r(2)))
    writer.finish()
    out.toByteArray

  private def parseJena(nquads: Array[Byte], quads: Boolean): IndexedSeq[Array[Node]] =
    val rows = mutable.ArrayBuffer.empty[Array[Node]]
    FairSettings
      .jenaParser(RDFParser.source(ByteArrayInputStream(nquads)).lang(Lang.NQUADS))
      .parse(new StreamRDFBase:
        override def triple(t: Triple): Unit =
          rows += (if quads then Array(t.getSubject, t.getPredicate, t.getObject, null)
                   else Array(t.getSubject, t.getPredicate, t.getObject))
        override def quad(q: Quad): Unit =
          val graph = if Quad.isDefaultGraph(q.getGraph) then null else q.getGraph
          rows += (if quads then Array(q.getSubject, q.getPredicate, q.getObject, graph)
                   else Array(q.getSubject, q.getPredicate, q.getObject)))
    rows.toIndexedSeq

  private def parseRdf4j(nquads: Array[Byte], quads: Boolean): IndexedSeq[Array[Value]] =
    val rows = mutable.ArrayBuffer.empty[Array[Value]]
    val parser = Rio.createParser(Rdf4jFormat.NQUADS)
    FairSettings.rdf4jParser(parser)
    parser.setRDFHandler(new AbstractRDFHandler:
      override def handleStatement(st: Statement): Unit =
        rows += (if quads then Array(st.getSubject, st.getPredicate, st.getObject, st.getContext)
                 else Array(st.getSubject, st.getPredicate, st.getObject)))
    parser.parse(ByteArrayInputStream(nquads))
    rows.toIndexedSeq
