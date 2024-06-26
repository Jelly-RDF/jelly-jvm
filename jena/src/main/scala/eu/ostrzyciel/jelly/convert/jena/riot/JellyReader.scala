package eu.ostrzyciel.jelly.convert.jena.riot

import eu.ostrzyciel.jelly.convert.jena.JenaConverterFactory
import eu.ostrzyciel.jelly.core.Constants.*
import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.apache.jena.atlas.web.ContentType
import org.apache.jena.graph.Triple
import org.apache.jena.riot.adapters.RDFReaderRIOT
import org.apache.jena.riot.system.{ParserProfile, StreamRDF}
import org.apache.jena.riot.{Lang, ReaderRIOT, ReaderRIOTFactory, RiotException}
import org.apache.jena.sparql.core.Quad
import org.apache.jena.sparql.util.Context

import java.io.{InputStream, Reader}

object JellyReaderFactory extends ReaderRIOTFactory:
  override def create(language: Lang, profile: ParserProfile): JellyReader.type = JellyReader

object JellyReader extends ReaderRIOT:
  override def read(reader: Reader, baseURI: String, ct: ContentType, output: StreamRDF, context: Context): Unit =
    throw new RiotException("RDF Jelly: Reading binary data from a java.io.Reader is not supported. " +
      "Please use an InputStream.")
  
  
  override def read(in: InputStream, baseURI: String, ct: ContentType, output: StreamRDF, context: Context): Unit =
    // Get the supported options specified by the user in the context -- or the default if not available
    val supportedOptions = context.get[RdfStreamOptions](
      JellyLanguage.SYMBOL_SUPPORTED_OPTIONS, JellyOptions.defaultSupportedOptions
    )
    val decoder = JenaConverterFactory.anyStatementDecoder(Some(supportedOptions))
    output.start()
    try {
      while in.available() > 0 do
        val frame = RdfStreamFrame.parseDelimitedFrom(in)
        frame match
          case Some(f) =>
            for row <- f.rows do
              decoder.ingestRow(row) match
                case Some(st: Triple) => output.triple(st)
                case Some(st: Quad) => output.quad(st)
                case None => ()
          case None => ()
    }
    finally {
      output.finish()
    }

object RDFReaderJelly extends RDFReaderRIOT(jellyName)
