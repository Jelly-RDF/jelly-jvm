package eu.ostrzyciel.jelly.integration_tests.io

import eu.ostrzyciel.jelly.convert.jena.riot.JellyLanguage
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import org.apache.jena.graph.Triple
import org.apache.jena.riot.system.{StreamRDFLib, StreamRDFWriter}
import org.apache.jena.riot.{RDFLanguages, RDFParser, RIOT}
import org.apache.jena.sparql.core.Quad

import java.io.{InputStream, OutputStream}

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

  override def readQuadsW3C(is: InputStream): Seq[Quad] =
    val sink = SinkSeq[Quad]()
    RDFParser.source(is)
      .lang(RDFLanguages.NQ)
      .parse(StreamRDFLib.sinkQuads(sink))
    sink.result

  override def readTriplesJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Triple] =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.defaultSupportedOptions))
    val sink = SinkSeq[Triple]()
    RDFParser.source(is)
      .lang(JellyLanguage.JELLY)
      .context(context)
      .parse(StreamRDFLib.sinkTriples(sink))
    sink.result

  override def readQuadsJelly(is: InputStream, supportedOptions: Option[RdfStreamOptions]): Seq[Quad] =
    val context = RIOT.getContext.copy()
      .set(JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, supportedOptions.getOrElse(JellyOptions.defaultSupportedOptions))
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
