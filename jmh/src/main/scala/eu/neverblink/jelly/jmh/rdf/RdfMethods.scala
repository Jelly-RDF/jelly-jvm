package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.convert.jena.riot.{JellyFormat, JellyLanguage}
import eu.neverblink.jelly.convert.rdf4j.rio.{JellyFormat as Rdf4jJellyFormat, JellyWriterSettings}
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.jmh.UnsyncByteArrayInputStream
import org.apache.jena.graph.Triple
import org.apache.jena.riot.system.{StreamRDFBase, StreamRDFWriter}
import org.apache.jena.riot.{RDFFormat, RDFParser}
import org.apache.jena.sparql.core.Quad
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.rio.helpers.{AbstractRDFHandler, BasicWriterSettings}
import org.eclipse.rdf4j.rio.{Rio, WriterConfig, RDFFormat as Rdf4jFormat}
import org.openjdk.jmh.infra.Blackhole

import java.io.{ByteArrayOutputStream, OutputStream}
import scala.annotation.nowarn
import scala.collection.mutable

/** Every way of writing and reading RDF statements used in [[RdfFormatBench]], each through the
  * library's own writer and parser.
  *
  * A method is named `<library>-<format>`, e.g. `jena-nt` or `rdf4j-jelly-big`. The format of a
  * dataset made of quads is the quads version of the named one: N-Quads for `nt`, TriG for `ttl`.
  */
object RdfMethods:

  sealed trait Method:
    def name: String

    /** Makes the library's statements, so that the benchmark does not measure that. */
    def prepare(data: RdfBenchData.Data): Unit

    def write(data: RdfBenchData.Data, out: OutputStream): Unit

    /** Reads the statements into the blackhole, and returns how many there were.
      *
      * @param quads
      *   whether the bytes hold quads
      */
    def read(bytes: Array[Byte], quads: Boolean, blackhole: Blackhole): Int

    /** Reads all the statements.
      *
      * Not [[read]] with another sink: the benchmark's read loop would then have seen two sinks by
      * the time it is measured, and the JIT may compile it differently from run to run.
      */
    def readAll(bytes: Array[Byte], quads: Boolean): collection.IndexedSeq[AnyRef]

    /** The statements that [[write]] writes, in this library's terms. */
    def original(data: RdfBenchData.Data): IndexedSeq[AnyRef]

    /** The subject, predicate, object and graph of a statement from [[original]] or [[readAll]].
      * The graph is null for a triple or for the default graph.
      */
    def terms(statement: AnyRef): Array[AnyRef]

    final def writeToBytes(data: RdfBenchData.Data): Array[Byte] =
      val out = ByteArrayOutputStream()
      write(data, out)
      out.toByteArray

  private final class JenaMethod(
      val name: String,
      triplesFormat: RDFFormat,
      quadsFormat: RDFFormat,
  ) extends Method:
    override def prepare(data: RdfBenchData.Data): Unit =
      val _ = if data.quads then data.jenaQuads else data.jenaTriples

    override def write(data: RdfBenchData.Data, out: OutputStream): Unit =
      val writer =
        StreamRDFWriter.getWriterStream(out, if data.quads then quadsFormat else triplesFormat)
      writer.start()
      var i = 0
      if data.quads then
        val quads = data.jenaQuads
        while i < quads.length do
          writer.quad(quads(i))
          i += 1
      else
        val triples = data.jenaTriples
        while i < triples.length do
          writer.triple(triples(i))
          i += 1
      writer.finish()

    override def read(bytes: Array[Byte], quads: Boolean, blackhole: Blackhole): Int =
      readInto(bytes, quads)(blackhole.consume)

    override def readAll(bytes: Array[Byte], quads: Boolean): collection.IndexedSeq[AnyRef] =
      val statements = mutable.ArrayBuffer.empty[AnyRef]
      readInto(bytes, quads)(statements += _)
      statements

    // Inlined, so that read and readAll each have their own copy of the parser's sink
    @nowarn("id=E197") // the copies of the sink class are the point
    private inline def readInto(bytes: Array[Byte], quads: Boolean)(
        inline sink: AnyRef => Unit,
    ): Int =
      var statements = 0
      RDFParser
        .source(UnsyncByteArrayInputStream(bytes))
        .lang((if quads then quadsFormat else triplesFormat).getLang)
        .parse(new StreamRDFBase:
          override def triple(triple: Triple): Unit =
            sink(triple)
            statements += 1
          override def quad(quad: Quad): Unit =
            sink(quad)
            statements += 1)
      statements

    override def original(data: RdfBenchData.Data): IndexedSeq[AnyRef] =
      if data.quads then data.jenaQuads.toIndexedSeq else data.jenaTriples.toIndexedSeq

    override def terms(statement: AnyRef): Array[AnyRef] = statement match
      case t: Triple => Array(t.getSubject, t.getPredicate, t.getObject, null)
      case q: Quad =>
        Array(
          q.getSubject,
          q.getPredicate,
          q.getObject,
          if Quad.isDefaultGraph(q.getGraph) then null else q.getGraph,
        )

  private final class Rdf4jMethod(
      val name: String,
      triplesFormat: Rdf4jFormat,
      quadsFormat: Rdf4jFormat,
      writerConfig: Boolean => Option[WriterConfig],
  ) extends Method:
    override def prepare(data: RdfBenchData.Data): Unit =
      val _ = data.rdf4j

    override def write(data: RdfBenchData.Data, out: OutputStream): Unit =
      val writer = Rio.createWriter(if data.quads then quadsFormat else triplesFormat, out)
      writerConfig(data.quads).foreach(writer.setWriterConfig)
      writer.startRDF()
      val statements = data.rdf4j
      var i = 0
      while i < statements.length do
        writer.handleStatement(statements(i))
        i += 1
      writer.endRDF()

    override def read(bytes: Array[Byte], quads: Boolean, blackhole: Blackhole): Int =
      readInto(bytes, quads)(blackhole.consume)

    override def readAll(bytes: Array[Byte], quads: Boolean): collection.IndexedSeq[AnyRef] =
      val statements = mutable.ArrayBuffer.empty[AnyRef]
      readInto(bytes, quads)(statements += _)
      statements

    // Inlined, so that read and readAll each have their own copy of the parser's handler
    @nowarn("id=E197") // the copies of the handler class are the point
    private inline def readInto(bytes: Array[Byte], quads: Boolean)(
        inline sink: AnyRef => Unit,
    ): Int =
      var statements = 0
      val parser = Rio.createParser(if quads then quadsFormat else triplesFormat)
      parser.setRDFHandler(new AbstractRDFHandler:
        override def handleStatement(st: Statement): Unit =
          sink(st)
          statements += 1)
      parser.parse(UnsyncByteArrayInputStream(bytes))
      statements

    override def original(data: RdfBenchData.Data): IndexedSeq[AnyRef] = data.rdf4j.toIndexedSeq

    override def terms(statement: AnyRef): Array[AnyRef] =
      val st = statement.asInstanceOf[Statement]
      Array(st.getSubject, st.getPredicate, st.getObject, st.getContext)

  private def jena(format: String, triples: RDFFormat, quads: RDFFormat): Method =
    JenaMethod(s"jena-$format", triples, quads)

  private def jenaJelly(preset: String, format: RDFFormat): Method =
    // Jelly takes triples and quads alike, and RIOT picks the stream type from the first statement
    JenaMethod(s"jena-jelly-$preset", format, format)

  private def rdf4j(
      format: String,
      triples: Rdf4jFormat,
      quads: Rdf4jFormat,
      config: Option[WriterConfig] = None,
  ): Method =
    Rdf4jMethod(s"rdf4j-$format", triples, quads, _ => config)

  private def rdf4jJelly(preset: String, options: RdfStreamOptions): Method =
    Rdf4jMethod(
      s"rdf4j-jelly-$preset",
      Rdf4jJellyFormat.JELLY,
      Rdf4jJellyFormat.JELLY,
      // Unlike Jena, RDF4J's writer does not pick the stream type itself, it writes quads unless
      // told otherwise
      quads =>
        Some(
          JellyWriterSettings.empty().setJellyOptions(
            options.clone().setPhysicalType(
              if quads then PhysicalStreamType.QUADS else PhysicalStreamType.TRIPLES,
            ),
          ),
        ),
    )

  // Jelly's own RIOT language must be registered before RDFParser looks it up
  JellyLanguage.register()

  val all: IndexedSeq[Method] = IndexedSeq(
    jena("nt", RDFFormat.NTRIPLES, RDFFormat.NQUADS),
    jena("ttl", RDFFormat.TURTLE_BLOCKS, RDFFormat.TRIG_BLOCKS),
    jena("thrift", RDFFormat.RDF_THRIFT, RDFFormat.RDF_THRIFT),
    jena("protobuf", RDFFormat.RDF_PROTO, RDFFormat.RDF_PROTO),
    jenaJelly("small", JellyFormat.JELLY_SMALL_STRICT),
    jenaJelly("big", JellyFormat.JELLY_BIG_STRICT),
    rdf4j("nt", Rdf4jFormat.NTRIPLES, Rdf4jFormat.NQUADS),
    // Pretty printing buffers the statements and groups them by subject, so the writer no longer
    // streams, and it changes the order and drops duplicates. Off, it streams like Jena's blocks.
    rdf4j(
      "ttl",
      Rdf4jFormat.TURTLE,
      Rdf4jFormat.TRIG,
      Some(WriterConfig().set(BasicWriterSettings.PRETTY_PRINT, false)),
    ),
    rdf4j("binary", Rdf4jFormat.BINARY, Rdf4jFormat.BINARY),
    rdf4jJelly("small", JellyOptions.SMALL_STRICT),
    rdf4jJelly("big", JellyOptions.BIG_STRICT),
  )

  private val byName: Map[String, Method] = all.map(m => m.name -> m).toMap

  def apply(name: String): Method =
    byName.getOrElse(
      name,
      throw IllegalArgumentException(
        s"Unknown method '$name'. Available: ${all.map(_.name).mkString(", ")}",
      ),
    )
