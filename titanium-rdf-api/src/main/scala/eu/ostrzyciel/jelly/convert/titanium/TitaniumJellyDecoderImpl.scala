package eu.ostrzyciel.jelly.convert.titanium

import com.apicatalog.rdf.api.RdfQuadConsumer
import eu.ostrzyciel.jelly.convert.titanium.Constants.*
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumConverterFactory
import eu.ostrzyciel.jelly.convert.titanium.internal.TitaniumRdf.*
import eu.ostrzyciel.jelly.core.proto.v1.*

class TitaniumJellyDecoderImpl(supportedOptions: RdfStreamOptions) extends TitaniumJellyDecoder:
  // Decode any physical stream type. Titanium only supports quads, but that's fine. We will
  // implicitly put triples in the default graph.
  private val decoder = TitaniumConverterFactory.anyStatementDecoder(
    supportedOptions = Some(supportedOptions)
  )

  override def ingestFrame(consumer: RdfQuadConsumer, frame: RdfStreamFrame): Unit =
    for row <- frame.rows do
      ingestRow(consumer, row)

  override def ingestRow(consumer: RdfQuadConsumer, row: RdfStreamRow): Unit =
    decoder.ingestRowFlat(row) match
      case null => ()
      case st: Quad => st.o match
        case iriLike: String =>
          consumer.quad(st.s, st.p, iriLike, null, null, null, st.g)
        case l: LangLiteral =>
          consumer.quad(st.s, st.p, l.lex, DT_LANG_STRING, l.lang, null, st.g)
        case s: SimpleLiteral =>
          consumer.quad(st.s, st.p, s.lex, DT_STRING, null, null, st.g)
        case d: DtLiteral =>
          consumer.quad(st.s, st.p, d.lex, d.dt, null, null, st.g)

  final override def getSupportedOptions: RdfStreamOptions = supportedOptions
