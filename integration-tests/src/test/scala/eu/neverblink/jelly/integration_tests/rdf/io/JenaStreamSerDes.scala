package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.convert.jena.riot.JellyLanguage
import eu.neverblink.jelly.core.JellyOptions
import eu.neverblink.jelly.core.proto.v1.RdfStreamOptions
import eu.neverblink.jelly.integration_tests.util.Measure
import org.apache.jena.graph.Triple
import org.apache.jena.riot.system.{StreamRDFLib, StreamRDFWriter}
import org.apache.jena.riot.{RDFLanguages, RDFParser, RIOT}
import org.apache.jena.sparql.core.Quad

import java.io.{File, InputStream, OutputStream, FileInputStream}

// Separate givens to avoid name clashes and ambiguous implicits
given mSeqTriples: Measure[Seq[Triple]] = (s: Seq[Triple]) => s.size
given mSeqQuads: Measure[Seq[Quad]] = (s: Seq[Quad]) => s.size

/**
 * Jena ser/des implementation using RIOT's streaming API (StreamRDF).
 */
object JenaStreamSerDes extends NativeSerDes[Seq[Triple], Seq[Quad]]:
  override def name: String = "Jena (StreamRDF)"

  override def readTriplesW3C(is: InputStream): Seq[Triple] =
    val sink = SinkSeq[Triple]()
    RDFParser.source(is)
      .lang(RDFLanguages.NT)
      .parse(StreamRDFLib.sinkTriples(sink))
    sink.result

  override def readTriplesW3C(streams: Seq[File]): Seq[Triple] =
    val sink = SinkSeq[Triple]()
    for stream <- streams do
      RDFParser.source(FileInputStream(stream))
        .lang(RDFLanguages.NT)
        .parse(StreamRDFLib.sinkTriples(sink))
    sink.result

  override def readQuadsW3C(is: InputStream): Seq[Quad] =
    val sink = SinkSeq[Quad]()
    RDFParser.source(is)
      .lang(RDFLanguages.NQ)
      .parse(StreamRDFLib.sinkQuads(sink))
    sink.result

  override def readQuadsW3C(files: Seq[File]): Seq[Quad] =
    val sink = SinkSeq[Quad]()
    for file <- files do
      RDFParser.source(FileInputStream(file))
        .lang(RDFLanguages.NQ)
        .parse(StreamRDFLib.sinkQuads(sink))
    sink.result

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Triple] =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS))
    val sink = SinkSeq[Triple]()
    RDFParser.source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(StreamRDFLib.sinkTriples(sink))
    sink.result

  override def readQuadsOrGraphsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Quad] =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.DEFAULT_SUPPORTED_OPTIONS))
    val sink = SinkSeq[Quad]()
    RDFParser.source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(StreamRDFLib.sinkQuads(sink))
    sink.result

  override def writeTriplesJelly(os: OutputStream, model: Seq[Triple], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_FRAME_SIZE, frameSize)
    if opt.isDefined then
      // Not setting the physical type, as it should be inferred from the data.
      // This emulates how RIOT initializes the stream writer in practice.
      context.set(JellyLanguage.SYMBOL_STREAM_OPTIONS, opt.get)

    val writerStream = StreamRDFWriter.getWriterStream(os, JellyLanguage.JELLY, context)
    writerStream.start()
    model.foreach(writerStream.triple)
    writerStream.finish()

  override def writeQuadsJelly(os: OutputStream, dataset: Seq[Quad], opt: Option[RdfStreamOptions], frameSize: Int): Unit =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_FRAME_SIZE, frameSize)
    if opt.isDefined then
      context.set(JellyLanguage.SYMBOL_STREAM_OPTIONS, opt.get)

    val writerStream = StreamRDFWriter.getWriterStream(os, JellyLanguage.JELLY, context)
    writerStream.start()
    dataset.foreach(writerStream.quad)
    writerStream.finish()

  private class SinkSeq[T] extends org.apache.jena.atlas.lib.Sink[T]:
    private val buffer = collection.mutable.ArrayBuffer.empty[T]

    override def send(item: T): Unit = buffer += item

    def result: Seq[T] = buffer.toSeq

    override def flush(): Unit = ()

    override def close(): Unit = ()
