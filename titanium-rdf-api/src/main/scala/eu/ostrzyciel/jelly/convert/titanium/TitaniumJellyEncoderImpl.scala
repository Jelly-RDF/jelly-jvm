package eu.ostrzyciel.jelly.convert.titanium

import com.apicatalog.rdf.api.{RdfConsumerException, RdfQuadConsumer}
import eu.ostrzyciel.jelly.convert.titanium.Constants.DT_STRING
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumConverterFactory
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumRdf.*
import eu.ostrzyciel.jelly.core.RdfProtoSerializationError
import eu.ostrzyciel.jelly.core.ProtoEncoder
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.immutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.*

private class TitaniumJellyEncoderImpl(options: RdfStreamOptions) extends TitaniumJellyEncoder:
  private val buffer: ListBuffer[RdfStreamRow] = new ListBuffer[RdfStreamRow]()
  private val encoder = TitaniumConverterFactory.encoder(ProtoEncoder.Params(
    // We set the stream type to QUADS, as this is the only type supported by Titanium.
    options = options.copy(
      physicalType = PhysicalStreamType.QUADS,
      logicalType = LogicalStreamType.FLAT_QUADS,
      // It's impossible to emit generalized statements or RDF-star in Titanium.
      generalizedStatements = false,
      rdfStar = false,
    ),
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
        else if datatype == DT_STRING then
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

  final override def getRowCount: Int = buffer.size

  final override def getRowsScala: Seq[RdfStreamRow] =
    val list = buffer.toList
    buffer.clear()
    list

  final override def getRowsJava: java.lang.Iterable[RdfStreamRow] = getRowsScala.asJava

  final override def getOptions: RdfStreamOptions = encoder.options
