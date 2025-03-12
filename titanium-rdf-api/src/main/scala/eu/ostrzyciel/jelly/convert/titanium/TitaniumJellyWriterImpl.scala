package eu.ostrzyciel.jelly.convert.titanium

import com.apicatalog.rdf.api.RdfQuadConsumer
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}

import java.io.OutputStream

class TitaniumJellyWriterImpl(
  outputStream: OutputStream, options: RdfStreamOptions, frameSize: Int,
) extends TitaniumJellyEncoderImpl(options) with TitaniumJellyWriter:

  override def quad(
    subject: String,
    predicate: String,
    `object`: String,
    datatype: String,
    language: String,
    direction: String,
    graph: String
  ): RdfQuadConsumer =
    super.quad(subject, predicate, `object`, datatype, language, direction, graph)
    if getRowCount >= frameSize then
      val frame = RdfStreamFrame(getRowsScala)
      frame.writeDelimitedTo(outputStream)
    this

  override def getOutputStream: OutputStream = outputStream

  final override def getFrameSize: Int = frameSize
