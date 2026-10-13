package eu.neverblink.jelly.jmh.rdf

import eu.neverblink.jelly.convert.jena.riot.{JellyFormat, JellyFormatVariant, JellyLanguage}
import eu.neverblink.jelly.convert.rdf4j.rio.{JellyFormat as Rdf4jJellyFormat, JellyWriterSettings}
import eu.neverblink.jelly.core.{JellyConstants, JellyOptions}
import eu.neverblink.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import eu.neverblink.jelly.jmh.{FairSettings, UnsyncByteArrayInputStream}
import org.apache.jena.graph.Triple
import org.apache.jena.riot.system.{StreamRDFBase, StreamRDFWriter}
import org.apache.jena.riot.{RDFFormat, RDFParser, RDFWriter}
import org.apache.jena.sparql.core.{DatasetGraphFactory, Quad}
import org.apache.jena.sparql.graph.GraphFactory
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
  *
  * Every writer except Jelly's is given the dataset's prefixes, before the first statement. Parsers
  * and writers use [[FairSettings]].
  */
object RdfMethods:

  sealed trait Method:
    def name: String

    /** Whether the writer collects all the statements before writing them, in an order of its own
      * and without duplicates. The round-trip check then compares the statements as sets.
      */
    def buffers: Boolean

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

  /** @param quadsFormat
    *   null if the format cannot hold quads
    * @param jelly
    *   whether the format is Jelly, which is not given prefixes
    */
  private final class JenaMethod(
      val name: String,
      triplesFormat: RDFFormat,
      quadsFormat: RDFFormat | Null,
      jelly: Boolean,
  ) extends Method:
    // Jena writes some formats only from a whole graph or dataset
    override val buffers: Boolean = !StreamRDFWriter.registered(triplesFormat)

    private def format(quads: Boolean): RDFFormat =
      if !quads then triplesFormat
      else if quadsFormat == null then
        throw UnsupportedOperationException(s"'$name' cannot write quads")
      else quadsFormat

    override def prepare(data: RdfBenchData.Data): Unit =
      val _ = if data.quads then data.jenaQuads else data.jenaTriples

    override def write(data: RdfBenchData.Data, out: OutputStream): Unit =
      if buffers then writeBuffered(data, out) else writeStream(data, out)

    /** Collects the statements into a graph or dataset, which is timed: RDF4J's buffering writers
      * do the same inside the writer.
      */
    private def writeBuffered(data: RdfBenchData.Data, out: OutputStream): Unit =
      val f = format(data.quads)
      if data.quads then
        val dataset = DatasetGraphFactory.create()
        if !jelly then data.prefixes.foreach((p, ns) => dataset.prefixes().add(p, ns))
        data.jenaQuads.foreach(dataset.add)
        FairSettings.jenaWriter(RDFWriter.source(dataset).format(f)).output(out)
      else
        val graph = GraphFactory.createDefaultGraph()
        if !jelly then data.prefixes.foreach((p, ns) => graph.getPrefixMapping.setNsPrefix(p, ns))
        data.jenaTriples.foreach(graph.add)
        FairSettings.jenaWriter(RDFWriter.source(graph).format(f)).output(out)

    private def writeStream(data: RdfBenchData.Data, out: OutputStream): Unit =
      val writer = StreamRDFWriter.getWriterStream(out, format(data.quads))
      writer.start()
      if !jelly then data.prefixes.foreach((p, ns) => writer.prefix(p, ns))
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
      FairSettings
        .jenaParser(
          RDFParser
            .source(UnsyncByteArrayInputStream(bytes))
            .lang(format(quads).getLang),
        )
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

  /** @param quadsFormat
    *   null if the format cannot hold quads (or only in an extension of RDF4J's own)
    * @param writerConfig
    *   the writer settings for triples (false) or quads (true), before [[FairSettings]]
    * @param jelly
    *   whether the format is Jelly, which is not given prefixes
    */
  private final class Rdf4jMethod(
      val name: String,
      triplesFormat: Rdf4jFormat,
      quadsFormat: Rdf4jFormat | Null,
      writerConfig: Boolean => WriterConfig,
      jelly: Boolean,
      val buffers: Boolean,
  ) extends Method:
    private def format(quads: Boolean): Rdf4jFormat =
      if !quads then triplesFormat
      else if quadsFormat == null then
        throw UnsupportedOperationException(s"'$name' cannot write quads")
      else quadsFormat

    override def prepare(data: RdfBenchData.Data): Unit =
      val _ = data.rdf4j

    override def write(data: RdfBenchData.Data, out: OutputStream): Unit =
      val writer = Rio.createWriter(format(data.quads), out)
      writer.setWriterConfig(FairSettings.rdf4jWriter(writerConfig(data.quads)))
      writer.startRDF()
      if !jelly then data.prefixes.foreach((p, ns) => writer.handleNamespace(p, ns))
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
      val parser = Rio.createParser(format(quads))
      FairSettings.rdf4jParser(parser)
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

  private def jena(format: String, triples: RDFFormat, quads: RDFFormat | Null): Method =
    JenaMethod(s"jena-$format", triples, quads, jelly = false)

  private def jenaJelly(preset: String, format: RDFFormat): Method =
    // Jelly takes triples and quads alike, and RIOT picks the stream type from the first statement
    JenaMethod(s"jena-jelly-$preset", format, format, jelly = true)

  /** The same as [[jenaJelly]], but writing Jelly-RDF 1.1 (row layout), for comparison. */
  private def jenaJelly11(preset: String, options: RdfStreamOptions): Method =
    val format = RDFFormat(
      JellyLanguage.JELLY,
      JellyFormatVariant.builder().options(rowLayout(options)).build(),
    )
    JenaMethod(s"jena-jelly11-$preset", format, format, jelly = true)

  /** The options asking for Jelly-RDF 1.1 output. Without namespace declarations, the stream is
    * written with protocol version 1, which has the same row layout.
    */
  private def rowLayout(options: RdfStreamOptions): RdfStreamOptions =
    options.clone().setVersion(JellyConstants.PROTO_VERSION_1_1_X)

  private def rdf4j(
      format: String,
      triples: Rdf4jFormat,
      quads: Rdf4jFormat | Null,
      config: => WriterConfig = WriterConfig(),
      buffers: Boolean = false,
  ): Method =
    Rdf4jMethod(s"rdf4j-$format", triples, quads, _ => config, jelly = false, buffers)

  private def rdf4jJelly(
      preset: String,
      options: RdfStreamOptions,
      name: String = "jelly",
  ): Method =
    Rdf4jMethod(
      s"rdf4j-$name-$preset",
      Rdf4jJellyFormat.JELLY,
      Rdf4jJellyFormat.JELLY,
      // Unlike Jena, RDF4J's writer does not pick the stream type itself, it writes quads unless
      // told otherwise
      quads =>
        JellyWriterSettings.empty().setJellyOptions(
          options.clone().setPhysicalType(
            if quads then PhysicalStreamType.QUADS else PhysicalStreamType.TRIPLES,
          ),
        ),
      jelly = true,
      buffers = false,
    )

  // Jelly's own RIOT language must be registered before RDFParser looks it up
  FairSettings.initJena()
  JellyLanguage.register()

  val all: IndexedSeq[Method] = IndexedSeq(
    jena("nt", RDFFormat.NTRIPLES, RDFFormat.NQUADS),
    jena("ttl", RDFFormat.TURTLE_BLOCKS, RDFFormat.TRIG_BLOCKS),
    jena("rdfxml", RDFFormat.RDFXML_PLAIN, null),
    // Expanded JSON-LD (no @context, so prefixes are not used)
    jena("jsonld", RDFFormat.JSONLD11_FLAT, RDFFormat.JSONLD11_FLAT),
    jena("rdfjson", RDFFormat.RDFJSON, null),
    jena("thrift", RDFFormat.RDF_THRIFT, RDFFormat.RDF_THRIFT),
    jena("protobuf", RDFFormat.RDF_PROTO, RDFFormat.RDF_PROTO),
    jenaJelly("small", JellyFormat.JELLY_SMALL_STRICT),
    jenaJelly("big", JellyFormat.JELLY_BIG_STRICT),
    jenaJelly11("small", JellyOptions.SMALL_STRICT),
    jenaJelly11("big", JellyOptions.BIG_STRICT),
    rdf4j("nt", Rdf4jFormat.NTRIPLES, Rdf4jFormat.NQUADS),
    // Pretty printing buffers the statements and groups them by subject, so the writer no longer
    // streams, and it changes the order and drops duplicates. Off, it streams like Jena's blocks.
    rdf4j(
      "ttl",
      Rdf4jFormat.TURTLE,
      Rdf4jFormat.TRIG,
      WriterConfig().set(BasicWriterSettings.PRETTY_PRINT, false),
    ),
    // Streams, grouping only consecutive statements with the same subject
    rdf4j("rdfxml", Rdf4jFormat.RDFXML, null),
    // Expanded JSON-LD, its default. Both collect all statements in a model before writing.
    rdf4j("jsonld", Rdf4jFormat.JSONLD, Rdf4jFormat.JSONLD, buffers = true),
    // RDF4J's RDF/JSON can hold quads, but that is RDF4J's own extension of the format
    rdf4j("rdfjson", Rdf4jFormat.RDFJSON, null, buffers = true),
    rdf4j("binary", Rdf4jFormat.BINARY, Rdf4jFormat.BINARY),
    rdf4jJelly("small", JellyOptions.SMALL_STRICT),
    rdf4jJelly("big", JellyOptions.BIG_STRICT),
    rdf4jJelly("small", rowLayout(JellyOptions.SMALL_STRICT), "jelly11"),
    rdf4jJelly("big", rowLayout(JellyOptions.BIG_STRICT), "jelly11"),
  )

  private val byName: Map[String, Method] = all.map(m => m.name -> m).toMap

  /** Jelly methods with any preset and frame size, named
    * `<jena|rdf4j>-<jelly|jelly11>-<small|big|sbig>-f<frame size>`, e.g. `jena-jelly-sbig-f1024`.
    * `sbig` is the BIG preset of Jelly-SPARQL (8192 names, 1024 prefixes, 64 datatypes).
    */
  private val tunedJelly = "(jena|rdf4j)-(jelly|jelly11)-(small|big|sbig)-f([0-9]+)".r

  private def presetOptions(preset: String): RdfStreamOptions = preset match
    case "small" => JellyOptions.SMALL_STRICT
    case "big" => JellyOptions.BIG_STRICT
    case _ =>
      JellyOptions.BIG_STRICT.clone()
        .setMaxNameTableSize(8192)
        .setMaxPrefixTableSize(1024)
        .setMaxDatatypeTableSize(64)

  private def tuned(name: String, library: String, layout: String, preset: String, frameSize: Int) =
    val base = presetOptions(preset)
    val options = if layout == "jelly11" then rowLayout(base) else base
    if library == "jena" then
      val format = RDFFormat(
        JellyLanguage.JELLY,
        JellyFormatVariant.builder().options(options).frameSize(frameSize).build(),
      )
      JenaMethod(name, format, format, jelly = true)
    else
      Rdf4jMethod(
        name,
        Rdf4jJellyFormat.JELLY,
        Rdf4jJellyFormat.JELLY,
        quads =>
          JellyWriterSettings.empty()
            .setJellyOptions(
              options.clone().setPhysicalType(
                if quads then PhysicalStreamType.QUADS else PhysicalStreamType.TRIPLES,
              ),
            )
            .setFrameSize(frameSize),
        jelly = true,
        buffers = false,
      )

  def apply(name: String): Method =
    byName.get(name) match
      case Some(method) => method
      case None =>
        name match
          case tunedJelly(library, layout, preset, frameSize) =>
            tuned(name, library, layout, preset, frameSize.toInt)
          case _ =>
            throw IllegalArgumentException(
              s"Unknown method '$name'. Available: ${all.map(_.name).mkString(", ")}, " +
                "or <jena|rdf4j>-<jelly|jelly11>-<small|big|sbig>-f<frame size>",
            )
