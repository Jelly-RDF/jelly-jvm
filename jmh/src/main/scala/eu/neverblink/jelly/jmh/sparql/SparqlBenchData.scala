package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.JenaConverterFactory
import eu.neverblink.jelly.convert.jena.sparql.JenaSparqlConverterFactory
import eu.neverblink.jelly.convert.jena.sparql.gen.JenaTermFactory
import eu.neverblink.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.neverblink.jelly.convert.rdf4j.sparql.gen.Rdf4jTermFactory
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.gen.{ResultSetSpec, SparqlDataGen}
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
import org.apache.jena.graph.Node
import org.apache.jena.sparql.core.{Quad, Var}
import org.apache.jena.sparql.engine.binding.{Binding, BindingFactory}
import org.apache.jena.sparql.exec.{RowSet, RowSetStream}
import org.apache.jena.sys.JenaSystem
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.impl.ListBindingSet

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.collection.immutable.ArraySeq
import scala.jdk.CollectionConverters.*

/** Shared fixture for the Jelly-SPARQL benchmarks.
  *
  * A dataset is either a synthetic preset from
  * [[eu.neverblink.jelly.core.sparql.gen.SparqlDataGen]] (the same generator that drives the
  * fuzzing tests) or a RiverBench dataset (see [[RiverBenchData]]). Both are loaded the same way,
  * at any row count:
  * {{{
  * val data = SparqlBenchData.load("nanopubs", 100_000)
  * }}}
  */
object SparqlBenchData:

  /** Options for the benchmarks that drive the core encoder directly. The format benchmarks use
    * their own, see [[SparqlMethods]].
    */
  val options: SparqlResultsOptions = JellySparqlOptions.BIG

  /** Every dataset that [[load]] accepts. */
  def datasetNames: IndexedSeq[String] = SparqlDataGen.presetNames ++ RiverBenchData.datasetNames

  /** A result set, ready to be fed to either library.
    *
    * The rows of each library are built on first use, at trial setup: node construction and binding
    * building are not what these benchmarks are about.
    */
  final class Data(
      val name: String,
      variableNames: Seq[String],
      jenaRows: => IndexedSeq[Array[Node]],
      rdf4jRows: => IndexedSeq[Array[Value]],
  ):
    // Every collection a library sees is a plain array-backed Java list. A Scala List wrapped with
    // asJava has O(n) size() and get(i), and RDF4J's ListBindingSet calls both for every value.
    val variables: java.util.List[String] = java.util.List.copyOf(variableNames.asJava)

    lazy val jena: JenaData = JenaData(variableNames, jenaRows)

    lazy val rdf4j: Rdf4jData = Rdf4jData(variables, rdf4jRows)

  final class JenaData(variableNames: Seq[String], rowSeq: IndexedSeq[Array[Node]]):
    /** Array-backed, so that iterating over it in a benchmark costs nothing. */
    val rows: IndexedSeq[Array[Node]] = ArraySeq.from(rowSeq)

    private val vars: Seq[Var] = variableNames.map(Var.alloc)

    val jenaVars: java.util.List[Var] = java.util.List.copyOf(vars.asJava)

    val bindings: java.util.List[Binding] = java.util.List.copyOf(rows.map { row =>
      val builder = BindingFactory.builder()
      for i <- row.indices if row(i) != null do builder.add(vars(i), row(i))
      builder.build()
    }.asJava)

    /** A fresh single-use RowSet over the pre-built bindings. */
    def rowSet(): RowSet = RowSetStream.create(jenaVars, bindings.iterator())

  final class Rdf4jData(variables: java.util.List[String], rowSeq: IndexedSeq[Array[Value]]):
    val rows: IndexedSeq[Array[Value]] = ArraySeq.from(rowSeq)

    /** Unbound variables are nulls, which ListBindingSet leaves out of the binding set. */
    val bindingSets: Array[BindingSet] =
      rows.map(row => ListBindingSet(variables, java.util.Arrays.asList(row*))).toArray

  /** Loads the first `rows` rows of a dataset. */
  def load(dataset: String, rows: Int): Data =
    require(rows > 0, s"Row count must be positive, got $rows")
    JenaSystem.init()
    if RiverBenchData.datasetNames.contains(dataset) then loadRiverBench(dataset, rows)
    else if SparqlDataGen.presetNames.contains(dataset) then
      generate(resize(SparqlDataGen.preset(dataset), rows))
    else
      throw IllegalArgumentException(
        s"Unknown dataset '$dataset'. Available: ${datasetNames.mkString(", ")}",
      )

  /** Generates the result set described by the spec, at the spec's own row count. */
  def generate(spec: ResultSetSpec): Data =
    JenaSystem.init()
    // Generation is deterministic, so both libraries see the exact same terms
    lazy val specRows = SparqlDataGen.generate(spec)
    Data(
      spec.name,
      spec.variables,
      JenaTermFactory.materializeRows(specRows),
      Rdf4jTermFactory.materializeRows(specRows),
    )

  /** Changes the row count of a preset, keeping its shape: pools, sparsity and runs stay the same,
    * and the row at which a polymorphic column starts mixing term types moves along with the row
    * count, so it still switches at the same point, relatively.
    */
  private def resize(spec: ResultSetSpec, rows: Int): ResultSetSpec =
    val factor = rows.toDouble / spec.rows
    spec.copy(
      rows = rows,
      columns = spec.columns.map(c => c.copy(mixStartRow = (c.mixStartRow * factor).toInt)),
    )

  private def loadRiverBench(dataset: String, rows: Int): Data =
    // Each library reads the file with its own Jelly decoder. Reading it twice is cheaper than
    // converting nodes between the libraries, and it only happens once per trial.
    lazy val jenaLoaded = RiverBenchData.load[Node](
      dataset,
      rows,
      JenaConverterFactory.getInstance(),
      new Array[Node](_),
      Quad.isDefaultGraph,
    )
    lazy val rdf4jLoaded = RiverBenchData.load[Value](
      dataset,
      rows,
      Rdf4jConverterFactory.getInstance(),
      new Array[Value](_),
      // RDF4J's decoder already returns null for the default graph
      _ == null,
    )
    // Whether the dataset has quads is needed up front, for the variable names. Peek at the first
    // statement instead of loading the whole thing just for that.
    val (quads, _) = RiverBenchData.load[Node](
      dataset,
      1,
      JenaConverterFactory.getInstance(),
      new Array[Node](_),
      Quad.isDefaultGraph,
    )
    Data(dataset, RiverBenchData.variables(quads), jenaLoaded._2, rdf4jLoaded._2)

  /** Frames are budgeted in values, so how many rows fit depends on the width of the result set. */
  def rowsPerFrame(data: Data, maxValuesPerFrame: Int): Int =
    math.max(1, maxValuesPerFrame / math.max(1, data.variables.size))

  /** Encodes the data with the core encoder, writing delimited frames to the given stream. */
  def encodeCore(data: Data, maxValuesPerFrame: Int, out: OutputStream): Unit =
    val encoder = JenaSparqlConverterFactory.getInstance().encoder(SparqlEncoder.Params.of(options))
    encoder.setVariables(data.variables)
    val rowLimit = rowsPerFrame(data, maxValuesPerFrame)
    var rowsInFrame = 0
    var wroteAnyFrame = false
    for row <- data.jena.rows do
      if !encoder.appendRow(row) then
        // The frame ran out of lookup entries before reaching the row limit
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
        encoder.appendRow(row)
      rowsInFrame += 1
      if rowsInFrame >= rowLimit then
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
    // The last frame carries the header even when there are no rows at all
    if rowsInFrame > 0 || !wroteAnyFrame then encoder.endFrame().writeDelimitedTo(out)

  def encodeToBytes(data: Data, maxValuesPerFrame: Int): Array[Byte] =
    val out = ByteArrayOutputStream()
    encodeCore(data, maxValuesPerFrame, out)
    out.toByteArray
