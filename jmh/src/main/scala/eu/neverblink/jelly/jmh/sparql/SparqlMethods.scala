package eu.neverblink.jelly.jmh.sparql

import eu.neverblink.jelly.convert.jena.sparql.{
  JenaSparqlConverterFactory,
  RowSetReaderJelly,
  RowSetWriterJelly,
}
import eu.neverblink.jelly.convert.rdf4j.sparql.{
  JellySparqlTupleParserFactory,
  JellySparqlTupleWriterFactory,
  JellySparqlWriterSettings,
}
import eu.neverblink.jelly.core.proto.v1.sparql.SparqlResultsOptions
import eu.neverblink.jelly.core.sparql.{JellySparqlConstants, JellySparqlOptions}
import org.apache.jena.riot.Lang
import org.apache.jena.riot.resultset.ResultSetLang
import org.apache.jena.sparql.engine.binding.Binding
import org.apache.jena.riot.rowset.{
  RowSetReader,
  RowSetReaderRegistry,
  RowSetWriter,
  RowSetWriterRegistry,
}
import org.eclipse.rdf4j.query.resultio.binary.{
  BinaryQueryResultParserFactory,
  BinaryQueryResultWriterFactory,
}
import org.eclipse.rdf4j.query.resultio.sparqljson.{
  SPARQLResultsJSONParserFactory,
  SPARQLResultsJSONWriterFactory,
}
import org.eclipse.rdf4j.query.resultio.sparqlods.SPARQLResultsODSWriterFactory
import org.eclipse.rdf4j.query.resultio.sparqlxml.{
  SPARQLResultsXMLParserFactory,
  SPARQLResultsXMLWriterFactory,
}
import org.eclipse.rdf4j.query.resultio.sparqlxslx.SPARQLResultsXLSXWriterFactory
import org.eclipse.rdf4j.query.resultio.text.csv.{
  SPARQLResultsCSVParserFactory,
  SPARQLResultsCSVWriterFactory,
}
import org.eclipse.rdf4j.query.resultio.text.tsv.{
  SPARQLResultsTSVParserFactory,
  SPARQLResultsTSVWriterFactory,
}
import org.eclipse.rdf4j.query.resultio.{
  TupleQueryResultParserFactory,
  TupleQueryResultWriterFactory,
}
import org.eclipse.rdf4j.query.{AbstractTupleQueryResultHandler, BindingSet}
import org.eclipse.rdf4j.rio.WriterConfig

import eu.neverblink.jelly.jmh.UnsyncByteArrayInputStream

import java.io.{ByteArrayOutputStream, OutputStream}

/** Every way of reading and writing a SPARQL result set used in benchmarks.
  *
  * A method is named `<library>-<format>`, e.g. `jena-srj` or `rdf4j-jelly-big`.
  */
object SparqlMethods:

  sealed trait Method:
    def name: String

    def canRead: Boolean
    
    def prepare(data: SparqlBenchData.Data): Unit

    def write(data: SparqlBenchData.Data, out: OutputStream): Unit
    
    def read(bytes: Array[Byte], sink: AnyRef => Unit): Int

    /** The rows that [[write]] writes, in this library's terms. */
    def original(data: SparqlBenchData.Data): IndexedSeq[Array[? <: AnyRef]]

    /** The value of a variable in a row passed to the sink of [[read]], or null if unbound. */
    def cell(row: AnyRef, variable: String): AnyRef

    final def writeToBytes(data: SparqlBenchData.Data): Array[Byte] =
      val out = ByteArrayOutputStream()
      write(data, out)
      out.toByteArray

  private final class JenaMethod(
      val name: String,
      newWriter: () => RowSetWriter,
      newReader: Option[() => RowSetReader],
  ) extends Method:
    override def canRead: Boolean = newReader.isDefined

    override def prepare(data: SparqlBenchData.Data): Unit =
      val _ = data.jena

    override def write(data: SparqlBenchData.Data, out: OutputStream): Unit =
      newWriter().write(out, data.jena.rowSet(), null)

    override def read(bytes: Array[Byte], sink: AnyRef => Unit): Int =
      val reader = newReader.getOrElse(throw UnsupportedOperationException(s"$name cannot read"))
      val rowSet = reader().read(UnsyncByteArrayInputStream(bytes), null)
      var rows = 0
      while rowSet.hasNext do
        sink(rowSet.next())
        rows += 1
      rows

    override def original(data: SparqlBenchData.Data): IndexedSeq[Array[? <: AnyRef]] =
      data.jena.rows

    override def cell(row: AnyRef, variable: String): AnyRef =
      row.asInstanceOf[Binding].get(variable)
  
  private def jena(format: String, lang: Lang, canRead: Boolean = true): Method =
    JenaMethod(
      s"jena-$format",
      () => RowSetWriterRegistry.getFactory(lang).create(lang),
      Option.when(canRead)(() => RowSetReaderRegistry.getFactory(lang).create(lang)),
    )

  private def jenaJelly(preset: String, options: SparqlResultsOptions): Method =
    val factory = JenaSparqlConverterFactory.getInstance()
    JenaMethod(
      s"jena-jelly-$preset",
      () =>
        RowSetWriterJelly(
          RowSetWriterJelly.Options(
            options,
            JellySparqlConstants.DEFAULT_MAX_VALUES_PER_FRAME,
            true,
          ),
          factory,
        ),
      Some(() => RowSetReaderJelly(RowSetReaderJelly.Options(), factory)),
    )

  private final class Rdf4jMethod(
      val name: String,
      writerFactory: TupleQueryResultWriterFactory,
      writerConfig: Option[WriterConfig],
      parserFactory: Option[TupleQueryResultParserFactory],
  ) extends Method:
    override def canRead: Boolean = parserFactory.isDefined

    override def prepare(data: SparqlBenchData.Data): Unit =
      val _ = data.rdf4j

    override def write(data: SparqlBenchData.Data, out: OutputStream): Unit =
      val writer = writerFactory.getWriter(out)
      writerConfig.foreach(writer.setWriterConfig)
      // This is what QueryResultIO.writeTuple does with a TupleQueryResult, minus the iteration.
      // Most writers start the document on their own, but the ODS one does not.
      writer.startDocument()
      writer.startHeader()
      writer.startQueryResult(data.variables)
      val bindingSets = data.rdf4j.bindingSets
      var i = 0
      while i < bindingSets.length do
        writer.handleSolution(bindingSets(i))
        i += 1
      writer.endQueryResult()

    override def read(bytes: Array[Byte], sink: AnyRef => Unit): Int =
      val factory =
        parserFactory.getOrElse(throw UnsupportedOperationException(s"$name cannot read"))
      val parser = factory.getParser
      var rows = 0
      parser.setQueryResultHandler(new AbstractTupleQueryResultHandler:
        override def handleSolution(bindingSet: BindingSet): Unit =
          sink(bindingSet)
          rows += 1)
      parser.parseQueryResult(UnsyncByteArrayInputStream(bytes))
      rows

    override def original(data: SparqlBenchData.Data): IndexedSeq[Array[? <: AnyRef]] =
      data.rdf4j.rows

    override def cell(row: AnyRef, variable: String): AnyRef =
      row.asInstanceOf[BindingSet].getValue(variable)

  private def rdf4j(
      format: String,
      writerFactory: TupleQueryResultWriterFactory,
      parserFactory: TupleQueryResultParserFactory | Null,
  ): Method =
    Rdf4jMethod(s"rdf4j-$format", writerFactory, None, Option(parserFactory))

  private def rdf4jJelly(preset: String, options: SparqlResultsOptions): Method =
    Rdf4jMethod(
      s"rdf4j-jelly-$preset",
      JellySparqlTupleWriterFactory(),
      Some(JellySparqlWriterSettings.empty().setJellyOptions(options)),
      Some(JellySparqlTupleParserFactory()),
    )

  val all: IndexedSeq[Method] = IndexedSeq(
    jena("srx", ResultSetLang.RS_XML),
    jena("srj", ResultSetLang.RS_JSON),
    jena("csv", ResultSetLang.RS_CSV),
    jena("tsv", ResultSetLang.RS_TSV),
    // The ASCII table printed by `arq` and `sparql`
    jena("text", ResultSetLang.RS_Text, canRead = false),
    jena("thrift", ResultSetLang.RS_Thrift),
    jena("protobuf", ResultSetLang.RS_Protobuf),
    jenaJelly("small", JellySparqlOptions.SMALL),
    jenaJelly("big", JellySparqlOptions.BIG),
    rdf4j("srx", SPARQLResultsXMLWriterFactory(), SPARQLResultsXMLParserFactory()),
    rdf4j("srj", SPARQLResultsJSONWriterFactory(), SPARQLResultsJSONParserFactory()),
    rdf4j("csv", SPARQLResultsCSVWriterFactory(), SPARQLResultsCSVParserFactory()),
    rdf4j("tsv", SPARQLResultsTSVWriterFactory(), SPARQLResultsTSVParserFactory()),
    rdf4j("binary", BinaryQueryResultWriterFactory(), BinaryQueryResultParserFactory()),
    rdf4j("xlsx", SPARQLResultsXLSXWriterFactory(), null),
    rdf4j("ods", SPARQLResultsODSWriterFactory(), null),
    rdf4jJelly("small", JellySparqlOptions.SMALL),
    rdf4jJelly("big", JellySparqlOptions.BIG),
  )

  private val byName: Map[String, Method] = all.map(m => m.name -> m).toMap

  def apply(name: String): Method =
    byName.getOrElse(
      name,
      throw IllegalArgumentException(
        s"Unknown method '$name'. Available: ${all.map(_.name).mkString(", ")}",
      ),
    )
