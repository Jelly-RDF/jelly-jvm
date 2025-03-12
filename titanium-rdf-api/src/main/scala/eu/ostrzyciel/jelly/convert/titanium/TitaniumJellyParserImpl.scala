package eu.ostrzyciel.jelly.convert.titanium

import com.apicatalog.rdf.api.RdfQuadConsumer
import eu.ostrzyciel.jelly.core.IoUtils
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}

import java.io.InputStream

final class TitaniumJellyParserImpl(supportedOptions: RdfStreamOptions)
  extends TitaniumJellyDecoderImpl(supportedOptions) with TitaniumJellyParser:

  override def parseAll(consumer: RdfQuadConsumer, inputStream: InputStream): Unit =
    parseInternal(consumer, inputStream, oneFrame = false)

  override def parseFrame(consumer: RdfQuadConsumer, inputStream: InputStream): Unit =
    parseInternal(consumer, inputStream, oneFrame = true)

  private def parseInternal(
    consumer: RdfQuadConsumer, inputStream: InputStream, oneFrame: Boolean
  ): Unit =
    IoUtils.autodetectDelimiting(inputStream) match
      case (false, newIn) =>
        // File contains a single frame
        val frame = RdfStreamFrame.parseFrom(newIn)
        processFrame(consumer, frame)
      case (true, newIn) =>
        // May contain multiple frames
        var it = Iterator.continually(RdfStreamFrame.parseDelimitedFrom(newIn))
          .takeWhile(_.isDefined)
        if oneFrame then it = it.take(1)
        it.foreach { maybeFrame => processFrame(consumer, maybeFrame.get) }

  private def processFrame(consumer: RdfQuadConsumer, frame: RdfStreamFrame): Unit =
    for row <- frame.rows do
      ingestRow(consumer, row)
