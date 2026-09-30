package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.jmh.sparql.RiverBenchData
import org.apache.jena.graph.{Node, Triple}
import org.apache.jena.sparql.core.Quad
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value}

/** RiverBench datasets as RDF statements, for [[RdfFormatBench]].
  *
  * {{{
  * val data = RdfBenchData.load("nanopubs", 100_000)
  * }}}
  */
object RdfBenchData:

  def datasetNames: IndexedSeq[String] = RiverBenchData.datasetNames

  /** The first `rows` statements of a dataset, in each library's terms. Each library's statements
    * are only made when that library is used.
    *
    * @param size
    *   how many statements there are
    * @param quads
    *   whether the dataset is made of quads (then the default graph is
    *   `Quad.defaultGraphNodeGenerated` in Jena and a null context in RDF4J) or of triples
    */
  final class Data(
      val name: String,
      val size: Int,
      val quads: Boolean,
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
    Data(dataset, rows, quads, jenaRows, rdf4jRows)
