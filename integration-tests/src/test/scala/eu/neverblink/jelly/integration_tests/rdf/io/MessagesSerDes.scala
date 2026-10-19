package eu.neverblink.jelly.integration_tests.rdf.io

import eu.neverblink.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import eu.neverblink.jelly.core.utils.IoUtils
import eu.neverblink.jelly.core.{JellyConverterFactory, JellyOptions, RdfEncoder, RdfHandler}
import eu.neverblink.protoc.java.runtime.DelimitedMessageWriter

import java.io.{File, FileInputStream, FileOutputStream}
import scala.collection.mutable
import scala.util.Using

/** Reads and writes Jelly-RDF 1.2 streams of RDF Messages (stream type MESSAGES), one Seq of
  * statements per message.
  */
trait MessagesSerDes[TTriple, TQuad]:
  def readTriplesMessagesJelly(file: File): Seq[Seq[TTriple]]
  def readQuadsMessagesJelly(file: File): Seq[Seq[TQuad]]

  def writeTriplesMessagesJelly(
      file: File,
      messages: Seq[Seq[TTriple]],
      opt: RdfStreamOptions,
      frameSize: Int,
  ): Unit
  def writeQuadsMessagesJelly(
      file: File,
      messages: Seq[Seq[TQuad]],
      opt: RdfStreamOptions,
      frameSize: Int,
  ): Unit

/** [[MessagesSerDes]] on top of the core API of an integration (its converter factory), as the RDF
  * library integrations themselves have no notion of messages.
  */
abstract class CoreMessagesSerDes[TNode, TTriple, TQuad](
    factory: JellyConverterFactory[TNode, ?, ?, ?],
) extends MessagesSerDes[TTriple, TQuad]:
  protected def makeTriple(s: TNode, p: TNode, o: TNode): TTriple
  protected def makeQuad(s: TNode, p: TNode, o: TNode, g: TNode): TQuad
  protected def writeTriple(encoder: RdfEncoder[TNode], triple: TTriple): Unit
  protected def writeQuad(encoder: RdfEncoder[TNode], quad: TQuad): Unit

  /** Collects the statements into messages. As in the reference decoder, the end of the stream ends
    * the open message only if it has statements.
    */
  private class MessageCollector[T]:
    val messages: mutable.ArrayBuffer[Seq[T]] = mutable.ArrayBuffer()
    private var current = mutable.ArrayBuffer[T]()

    def add(statement: T): Unit = current += statement

    def end(): Unit =
      messages += current.toSeq
      current = mutable.ArrayBuffer()

    def result: Seq[Seq[T]] =
      if current.nonEmpty then end()
      messages.toSeq

  private def readFrames(file: File)(ingest: RdfStreamFrame => Unit): Unit =
    Using.resource(FileInputStream(file)) { is =>
      IoUtils.readStream(is, RdfStreamFrame.getFactory, (frame: RdfStreamFrame) => ingest(frame))
    }

  override def readTriplesMessagesJelly(file: File): Seq[Seq[TTriple]] =
    val collector = MessageCollector[TTriple]()
    val decoder = factory.triplesDecoder(
      new RdfHandler.TripleHandler[TNode] {
        override def handleTriple(s: TNode, p: TNode, o: TNode): Unit =
          collector.add(makeTriple(s, p, o))
        override def handleMessageEnd(): Unit = collector.end()
      },
      JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    )
    readFrames(file)(decoder.ingestFrame)
    collector.result

  override def readQuadsMessagesJelly(file: File): Seq[Seq[TQuad]] =
    val collector = MessageCollector[TQuad]()
    val decoder = factory.quadsDecoder(
      new RdfHandler.QuadHandler[TNode] {
        override def handleQuad(s: TNode, p: TNode, o: TNode, g: TNode): Unit =
          collector.add(makeQuad(s, p, o, g))
        override def handleMessageEnd(): Unit = collector.end()
      },
      JellyOptions.DEFAULT_SUPPORTED_OPTIONS,
    )
    readFrames(file)(decoder.ingestFrame)
    collector.result

  private def write[T](
      file: File,
      messages: Seq[Seq[T]],
      opt: RdfStreamOptions,
      frameSize: Int,
      writeStatement: (RdfEncoder[TNode], T) => Unit,
  ): Unit =
    Using.resource(FileOutputStream(file)) { os =>
      val frames = DelimitedMessageWriter(os)
      val encoder = factory.encoder(RdfEncoder.Params.of(opt, frameSize, frames.write(_)))
      for message <- messages do
        message.foreach(writeStatement(encoder, _))
        encoder.handleMessageEnd()
      encoder.flush()
      frames.flush()
    }

  override def writeTriplesMessagesJelly(
      file: File,
      messages: Seq[Seq[TTriple]],
      opt: RdfStreamOptions,
      frameSize: Int,
  ): Unit = write(file, messages, opt, frameSize, writeTriple)

  override def writeQuadsMessagesJelly(
      file: File,
      messages: Seq[Seq[TQuad]],
      opt: RdfStreamOptions,
      frameSize: Int,
  ): Unit = write(file, messages, opt, frameSize, writeQuad)
