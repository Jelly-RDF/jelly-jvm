package eu.ostrzyciel.jelly.convert.titanium

import com.apicatalog.rdf.api.{RdfConsumerException, RdfQuadConsumer}
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumConverterFactory
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumRdf.*
import eu.ostrzyciel.jelly.core.RdfProtoSerializationError
import eu.ostrzyciel.jelly.core.ProtoEncoder
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamRow}

import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

private final class TitaniumJellyEncoderImpl(options: RdfStreamOptions) extends TitaniumJellyEncoder:

  private val buffer: ListBuffer[RdfStreamRow] = new ListBuffer[RdfStreamRow]()
  // We don't set any options here – it is the responsibility of the caller!
  private val encoder = TitaniumConverterFactory.encoder(ProtoEncoder.Params(
    options = options,
    enableNamespaceDeclarations = false,
    maybeRowBuffer = Some(buffer),
  ))

  override def quad(
    subject: String, 
    predicate: String,
    `object`: String, 
    datatype: String, 
    language: String, 
    direction: String,
    graph: String
  ): RdfQuadConsumer =
    // IRIs and bnodes don't need further processing. For literals, we must allocate
    // intermediate objects.
    try {
      if RdfQuadConsumer.isLiteral(datatype, language, direction) then
        val literal = if RdfQuadConsumer.isLangString(datatype, language, direction) then
          LangLiteral(`object`, language)
        else if datatype == "http://www.w3.org/2001/XMLSchema#string" then
          SimpleLiteral(`object`)
        else
          DtLiteral(`object`, datatype)
        encoder.addQuadStatement(subject, predicate, literal, graph)
      else
        encoder.addQuadStatement(subject, predicate, `object`, graph)
    } catch {
      case e: RdfProtoSerializationError => throw new RdfConsumerException(e.getMessage, e)
    }
    this

  override def getRowCount: Int = buffer.size

  override def getRowsScala: immutable.Iterable[RdfStreamRow] =
    val list = buffer.toList
    buffer.clear()
    list

  override def getRowsJava: java.lang.Iterable[RdfStreamRow] =
    val list = buffer.toList
    buffer.clear()
    list.asJava
