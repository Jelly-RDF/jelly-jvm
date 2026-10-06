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
  JellySparqlParserSettings,
  JellySparqlTupleParser,
  JellySparqlTupleWriter,
  JellySparqlWriterSettings,
  Rdf4jSparqlConverterFactory,
}
import eu.neverblink.jelly.core.helpers.Mrl
import eu.neverblink.jelly.core.proto.v1.sparql.{SparqlResultsFrame, SparqlResultsOptions}
import eu.neverblink.jelly.core.proto.v1.RdfBaseDirection
import eu.neverblink.jelly.core.sparql.gen.{MrlTermFactory, SparqlDataGen, TermFactory, TermSpec}
import eu.neverblink.jelly.core.sparql.helpers.{MockSparqlConverterFactory, ResultsCollector}
import eu.neverblink.jelly.core.sparql.{JellySparqlOptions, SparqlEncoder}
import org.apache.jena.graph.{Node, TextDirection}
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.engine.binding.BindingFactory
import org.apache.jena.sparql.exec.{QueryExecResult, RowSet, RowSetStream}
import org.eclipse.rdf4j.model.{BNode, IRI, Literal, TripleTerm, Value}
import org.eclipse.rdf4j.query.{BindingSet, QueryResultHandler}
import org.eclipse.rdf4j.query.impl.ListBindingSet
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

/** One Jelly-SPARQL implementation, as seen by the cross-implementation tests.
  */
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

  /** Converts a decoded node back into a library-independent term. */
  def toSpec(node: TNode): TermSpec

  /** Reads a whole stream, of either kind: a boolean result, or the variables and rows. Throws if
    * the stream is invalid – including errors that only show up once all rows are read.
    */
  def read(bytes: Array[Byte]): SparqlImplementation.Result

  /** Reads a whole stream that may have a sequence of result sets (PUNCTUATED). */
  def readAll(bytes: Array[Byte]): Seq[SparqlImplementation.Result] =
    throw UnsupportedOperationException(s"$name does not read sequences of result sets")

  /** Writes the results as the result sets of a PUNCTUATED stream. */
  def writeAll(
      results: Seq[SparqlImplementation.Result],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    throw UnsupportedOperationException(s"$name does not write sequences of result sets")

  protected final def toSpecRow(row: Seq[TNode | Null]): IndexedSeq[TermSpec | Null] =
    row.map(n => if n == null then null else toSpec(n.asInstanceOf[TNode])).toIndexedSeq

  final def encode(
      vars: Seq[String],
      generated: SparqlDataGen.Rows,
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    encodeRows(vars, termFactory.materializeRows(generated), maxValuesPerFrame, options)

  final def expected(generated: SparqlDataGen.Rows): Seq[Seq[Any]] =
    termFactory.materializeRows(generated).map(_.toSeq)

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
    for row <- rows do
      if !encoder.appendRow(row) then
        // The frame ran out of lookup entries before reaching the row limit
        encoder.endFrame().writeDelimitedTo(out)
        rowsInFrame = 0
        // An empty frame always takes the row
        encoder.appendRow(row)
      rowsInFrame += 1
      if rowsInFrame >= rowLimit then
        encoder.endFrame().writeDelimitedTo(out)
        rowsInFrame = 0
    // The last frame contains the trailer, and the header too if nothing was written before
    encoder.endStream().writeDelimitedTo(out)
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

  override def toSpec(node: TNode): TermSpec = node match
    case Mrl.Iri(iri) => TermSpec.Iri(iri)
    case Mrl.BlankNode(label) => TermSpec.BNode(label)
    case Mrl.SimpleLiteral(lex) => TermSpec.PlainLiteral(lex)
    case Mrl.LangLiteral(lex, lang) => TermSpec.LangLiteral(lex, lang)
    case Mrl.DirLangLiteral(lex, lang, direction) =>
      TermSpec.DirLangLiteral(lex, lang, direction == RdfBaseDirection.LTR)
    case Mrl.DtLiteral(lex, Mrl.Datatype(dt)) if dt == SparqlImplementation.XsdString =>
      TermSpec.PlainLiteral(lex)
    case Mrl.DtLiteral(lex, Mrl.Datatype(dt)) => TermSpec.DtLiteral(lex, dt)
    case Mrl.TripleNode(s, p, o) =>
      TermSpec.TripleTerm(
        toSpec(s.asInstanceOf[TNode]),
        toSpec(p.asInstanceOf[TNode]),
        toSpec(o.asInstanceOf[TNode]),
      )
    case other => throw IllegalArgumentException(s"Not an RDF term: $other")

  // The core decoder has no notion of a whole stream, so this only checks what it can
  override def read(bytes: Array[Byte]): SparqlImplementation.Result =
    val collector = ingest(bytes)
    collector.askResult match
      case Some(value) => Left(value)
      case None =>
        Right((collector.variables.toSeq, collector.rows.toSeq.map(r => toSpecRow(r))))

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
    val out = ByteArrayOutputStream()
    writer(maxValuesPerFrame, options).write(out, rowSet(vars, rows), null)
    out.toByteArray

  private def rowSet(vars: Seq[String], rows: IndexedSeq[Array[Node]]): RowSet =
    val jenaVars = vars.map(Var.alloc)
    val bindings = rows.map { row =>
      val builder = BindingFactory.builder()
      for i <- row.indices if row(i) != null do builder.add(jenaVars(i), row(i))
      builder.build()
    }
    RowSetStream.create(jenaVars.asJava, bindings.iterator.asJava)

  override def writeAll(
      results: Seq[SparqlImplementation.Result],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val out = ByteArrayOutputStream()
    val resultSets = writer(maxValuesPerFrame, options).resultSetsWriter(out, null)
    for result <- results do
      result match
        case Left(value) => resultSets.write(value)
        case Right((vars, rows)) =>
          resultSets.write(rowSet(vars, termFactory.materializeRows(rows.toIndexedSeq)))
    out.toByteArray

  override def readAll(bytes: Array[Byte]): Seq[SparqlImplementation.Result] =
    val results = ListBuffer[SparqlImplementation.Result]()
    reader().readAll(ByteArrayInputStream(bytes), null, result => results += toResult(result))
    results.toSeq

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

  override def toSpec(node: Node): TermSpec =
    if node.isURI then TermSpec.Iri(node.getURI)
    else if node.isBlank then TermSpec.BNode(node.getBlankNodeLabel)
    else if node.isTripleTerm then
      val t = node.getTriple
      TermSpec.TripleTerm(toSpec(t.getSubject), toSpec(t.getPredicate), toSpec(t.getObject))
    else if node.getLiteralLanguage.nonEmpty && node.getLiteralBaseDirection != null then
      TermSpec.DirLangLiteral(
        node.getLiteralLexicalForm,
        node.getLiteralLanguage,
        node.getLiteralBaseDirection == TextDirection.LTR,
      )
    else if node.getLiteralLanguage.nonEmpty then
      TermSpec.LangLiteral(node.getLiteralLexicalForm, node.getLiteralLanguage)
    else if node.getLiteralDatatypeURI == SparqlImplementation.XsdString then
      TermSpec.PlainLiteral(node.getLiteralLexicalForm)
    else TermSpec.DtLiteral(node.getLiteralLexicalForm, node.getLiteralDatatypeURI)

  override def read(bytes: Array[Byte]): SparqlImplementation.Result =
    toResult(reader().readAny(ByteArrayInputStream(bytes), null))

  private def toResult(result: QueryExecResult): SparqlImplementation.Result =
    if result.isBoolean then Left(result.booleanResult())
    else
      val rowSet = result.rowSet()
      val jenaVars = rowSet.getResultVars.asScala.toSeq
      // Reads all rows, so that errors at the end of the stream show up
      val rows = rowSet.asScala.map(binding => toSpecRow(jenaVars.map(binding.get))).toSeq
      Right((jenaVars.map(_.getVarName), rows))

  private def writer(maxValuesPerFrame: Int, options: SparqlResultsOptions) =
    RowSetWriterJelly(
      RowSetWriterJelly.Options(options, maxValuesPerFrame, true),
      JenaSparqlConverterFactory.getInstance(),
    )

  private def reader() =
    RowSetReaderJelly(RowSetReaderJelly.Options(), JenaSparqlConverterFactory.getInstance())

/** RDF4J, through the query result writers and parsers. */
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

  override def writeAll(
      results: Seq[SparqlImplementation.Result],
      maxValuesPerFrame: Int,
      options: SparqlResultsOptions,
  ): Array[Byte] =
    val out = ByteArrayOutputStream()
    val writer = JellySparqlTupleWriter(Rdf4jSparqlConverterFactory.getInstance(), out)
    writer.setWriterConfig(
      JellySparqlWriterSettings
        .empty()
        .setJellyOptions(options)
        .setPunctuated(true)
        .setMaxValuesPerFrame(maxValuesPerFrame),
    )
    for result <- results do
      result match
        case Left(value) => writer.handleBoolean(value)
        case Right((vars, rows)) =>
          val javaVars = vars.asJava
          writer.startQueryResult(javaVars)
          for row <- termFactory.materializeRows(rows.toIndexedSeq) do
            writer.handleSolution(ListBindingSet(javaVars, row.toSeq.asJava))
          writer.endQueryResult()
    out.toByteArray

  override def readAll(bytes: Array[Byte]): Seq[SparqlImplementation.Result] =
    val collector = ResultSetsCollector()
    val parser = JellySparqlTupleParser()
    parser.getParserConfig.set(JellySparqlParserSettings.PUNCTUATED, true)
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    collector.results.toSeq

  /** Collects every result set that the parser passes on. */
  private final class ResultSetsCollector extends QueryResultHandler:
    val results: ListBuffer[SparqlImplementation.Result] = ListBuffer()
    private var names: Seq[String] = Nil
    private val rows = ListBuffer[IndexedSeq[TermSpec | Null]]()

    override def handleBoolean(value: Boolean): Unit = results += Left(value)
    override def handleLinks(linkUrls: java.util.List[String]): Unit = ()
    override def startQueryResult(bindingNames: java.util.List[String]): Unit =
      names = bindingNames.asScala.toSeq
      rows.clear()
    override def endQueryResult(): Unit = results += Right((names, rows.toSeq))
    override def handleSolution(bindingSet: BindingSet): Unit =
      rows += toSpecRow(names.map(bindingSet.getValue))

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

  override def toSpec(value: Value): TermSpec = value match
    case iri: IRI => TermSpec.Iri(iri.stringValue)
    case bnode: BNode => TermSpec.BNode(bnode.getID)
    case triple: TripleTerm =>
      TermSpec.TripleTerm(
        toSpec(triple.getSubject),
        toSpec(triple.getPredicate),
        toSpec(triple.getObject),
      )
    case literal: Literal if literal.getLanguage.isPresent =>
      literal.getBaseDirection match
        case Literal.BaseDirection.NONE =>
          TermSpec.LangLiteral(literal.getLabel, literal.getLanguage.get)
        case direction =>
          TermSpec.DirLangLiteral(
            literal.getLabel,
            literal.getLanguage.get,
            direction == Literal.BaseDirection.LTR,
          )
    case literal: Literal if literal.getDatatype.stringValue == SparqlImplementation.XsdString =>
      TermSpec.PlainLiteral(literal.getLabel)
    case literal: Literal => TermSpec.DtLiteral(literal.getLabel, literal.getDatatype.stringValue)
    case other => throw IllegalArgumentException(s"Not an RDF term: $other")

  // The tuple parser also takes boolean results, and passes them on to the handler
  override def read(bytes: Array[Byte]): SparqlImplementation.Result =
    val collector = QueryResultCollector()
    val parser = JellySparqlTupleParser()
    parser.setQueryResultHandler(collector)
    parser.parseQueryResult(ByteArrayInputStream(bytes))
    if collector.getHandledBoolean then Left(collector.getBoolean)
    else
      val names = collector.getBindingNames.asScala.toSeq
      val rows = collector.getBindingSets.asScala.map(bs => toSpecRow(names.map(bs.getValue))).toSeq
      Right((names, rows))

object SparqlImplementation:
  /** A whole result: a boolean, or the variables and the rows. Unbound cells are nulls. */
  type Result = Either[Boolean, (Seq[String], Seq[IndexedSeq[TermSpec | Null]])]

  val XsdString = "http://www.w3.org/2001/XMLSchema#string"

  val all: Seq[SparqlImplementation] =
    Seq(CoreImplementation, JenaImplementation, Rdf4jImplementation)
