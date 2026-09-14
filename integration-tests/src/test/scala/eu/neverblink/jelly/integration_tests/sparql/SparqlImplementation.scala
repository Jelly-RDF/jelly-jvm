package eu.neverblink.jelly.integration_tests.sparql

import eu.neverblink.jelly.convert.jena.sparql.gen.JenaTermFactory
import eu.neverblink.jelly.convert.jena.sparql.{
  JenaSparqlConverterFactory,
  RowSetReaderJelly,
  RowSetWriterJelly,
}
import eu.neverblink.jelly.convert.rdf4j.sparql.gen.Rdf4jTermFactory
import eu.neverblink.jelly.convert.rdf4j.sparql.{
  JellySparqlBooleanParser,
  JellySparqlBooleanWriter,
  JellySparqlTupleParser,
  JellySparqlTupleWriter,
  JellySparqlWriterSettings,
  Rdf4jSparqlConverterFactory,
}
import eu.neverblink.jelly.core.helpers.Mrl
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsOptions}
import eu.neverblink.jelly.core.sparql.gen.{MrlTermFactory, SparqlDataGen, TermFactory}
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
import org.apache.jena.graph.Node
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.BindingFactory
import org.apache.jena.sparql.exec.RowSetStream
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.query.impl.ListBindingSet
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.annotation.experimental
import scala.jdk.CollectionConverters.*

/** One Jelly-SPARQL implementation, as seen by the cross-implementation tests.
  */
@experimental
trait SparqlImplementation:
  type TNode

  def name: String
  protected def termFactory: TermFactory[TNode]

  protected def encodeRows(
      vars: Seq[String],
      rows: IndexedSeq[Array[TNode]],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte]

  /** Decodes a solution sequence into (variable names, rows). Unbound cells are nulls. */
  def decode(bytes: Array[Byte]): (Seq[String], Seq[Seq[Any]])

  def encodeAsk(value: Boolean, options: SparqlResultsOptions): Array[Byte]
  def decodeAsk(bytes: Array[Byte]): Boolean

  final def encode(
      vars: Seq[String],
      generated: SparqlDataGen.Rows,
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    encodeRows(vars, termFactory.materializeRows(generated), maxValuesPerFrame, options)

  final def expected(generated: SparqlDataGen.Rows): Seq[Seq[Any]] =
    termFactory.materializeRows(generated).map(_.toSeq)

@experimental
object CoreImplementation extends SparqlImplementation:
  type TNode = Mrl.Node & Object

  override val name = "core"
  override protected val termFactory = MrlTermFactory

  override protected def encodeRows(
      vars: Seq[String],
      rows: IndexedSeq[Array[TNode]],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val encoder = MockSparqlConverterFactory.encoder(SparqlEncoder.Params.of(options))
    encoder.setVariables(vars.asJava)
    val out = ByteArrayOutputStream()
    val rowLimit = math.max(1, maxValuesPerFrame / math.max(1, vars.size))
    var rowsInFrame = 0
    var wroteAnyFrame = false
    for row <- rows do
      if !encoder.appendRow(row) then
        // The frame ran out of lookup entries before reaching the row limit
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
        // An empty frame always takes the row
        encoder.appendRow(row)
      rowsInFrame += 1
      if rowsInFrame >= rowLimit then
        encoder.endFrame().writeDelimitedTo(out)
        wroteAnyFrame = true
        rowsInFrame = 0
    // The last (possibly empty) frame still carries the header
    if rowsInFrame > 0 || !wroteAnyFrame then encoder.endFrame().writeDelimitedTo(out)
    out.toByteArray

  override def decode(bytes: Array[Byte]): (Seq[String], Seq[Seq[Any]]) =
    val collector = ingest(bytes)
    (collector.variables.toSeq, collector.rows.toSeq)

  override def encodeAsk(value: Boolean, options: SparqlResultsOptions): Array[Byte] =
    val out = ByteArrayOutputStream()
    SparqlEncoder.askResultFrame(options, value).writeDelimitedTo(out)
    out.toByteArray

  override def decodeAsk(bytes: Array[Byte]): Boolean =
    ingest(bytes).askResult.get

  private def ingest(bytes: Array[Byte]): ResultsCollector =
    val collector = ResultsCollector()
    val decoder =
      MockSparqlConverterFactory.decoder(collector, JellySparqlOptions.DEFAULT_SUPPORTED_OPTIONS)
    val in = ByteArrayInputStream(bytes)
    var frame = SparqlResultsFrame.parseDelimitedFrom(in)
    while frame != null do
      decoder.ingestFrame(frame)
      frame = SparqlResultsFrame.parseDelimitedFrom(in)
    collector

/** Apache Jena, through the RowSet reader and writer. */
@experimental
object JenaImplementation extends SparqlImplementation:
  type TNode = Node

  override val name = "jena"
  override protected val termFactory = JenaTermFactory

  override protected def encodeRows(
      vars: Seq[String],
      rows: IndexedSeq[Array[Node]],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val jenaVars = vars.map(Var.alloc)
    val bindings = rows.map { row =>
      val builder = BindingFactory.builder()
      for i <- row.indices if row(i) != null do builder.add(jenaVars(i), row(i))
      builder.build()
    }
    val out = ByteArrayOutputStream()
    writer(maxValuesPerFrame, options)
      .write(out, RowSetStream.create(jenaVars.asJava, bindings.iterator.asJava), null)
    out.toByteArray

  override def decode(bytes: Array[Byte]): (Seq[String], Seq[Seq[Any]]) =
    val rowSet = reader().read(ByteArrayInputStream(bytes), null)
    val jenaVars = rowSet.getResultVars.asScala.toSeq
    val rows = rowSet.asScala.map(binding => jenaVars.map(binding.get)).toSeq
    (jenaVars.map(_.getVarName), rows)

  override def encodeAsk(value: Boolean, options: SparqlResultsOptions): Array[Byte] =
    val out = ByteArrayOutputStream()
    writer(1, options).write(out, value, null)
    out.toByteArray

  override def decodeAsk(bytes: Array[Byte]): Boolean =
    reader().readAny(ByteArrayInputStream(bytes), null).booleanResult()

  private def writer(maxValuesPerFrame: Int, options: SparqlResultsOptions) =
    RowSetWriterJelly(
      RowSetWriterJelly.Options(options, maxValuesPerFrame, true),
      JenaSparqlConverterFactory.getInstance(),
    )

  private def reader() =
    RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())

/** RDF4J, through the query result writers and parsers. */
@experimental
object Rdf4jImplementation extends SparqlImplementation:
  type TNode = Value

  override val name = "rdf4j"
  override protected val termFactory = Rdf4jTermFactory

  override protected def encodeRows(
      vars: Seq[String],
      rows: IndexedSeq[Array[Value]],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(
      JellySparqlWriterSettings
        .empty()
        .setJellyOptions(options)
        .setMaxValuesPerFrame(maxValuesPerFrame),
    )
    val javaVars = vars.asJava
    writer.startQueryResult(javaVars)
    // ListBindingSet keeps unbound cells as nulls, which is what the writer expects
    for row <- rows do writer.handleSolution(ListBindingSet(javaVars, row.toSeq.asJava))
    writer.endQueryResult()
    out.toByteArray

  override def decode(bytes: Array[Byte]): (Seq[String], Seq[Seq[Any]]) =
    val collector = QueryResultCollector()
    val parser = JellySparqlTupleParser()
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    val names = collector.getBindingNames.asScala.toSeq
    val rows = collector.getBindingSets.asScala.map(bs => names.map(n => bs.getValue(n))).toSeq
    (names, rows)

  override def encodeAsk(value: Boolean, options: SparqlResultsOptions): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlBooleanWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(JellySparqlWriterSettings.empty().setJellyOptions(options))
    writer.write(value)
    out.toByteArray

  override def decodeAsk(bytes: Array[Byte]): Boolean =
    val collector = QueryResultCollector()
    val parser = JellySparqlBooleanParser()
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    collector.getBoolean

@experimental
object SparqlImplementation:
  val all: Seq[SparqlImplementation] =
    Seq(CoreImplementation, JenaImplementation, Rdf4jImplementation)
