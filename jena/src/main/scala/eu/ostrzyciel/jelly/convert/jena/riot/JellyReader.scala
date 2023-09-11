package eu.ostrzyciel.jelly.convert.jena.riot

import org.apache.jena.atlas.web.ContentType
import org.apache.jena.riot.adapters.RDFReaderRIOT
import org.apache.jena.riot.system.{ParserProfile, StreamRDF}
import org.apache.jena.riot.{Lang, ReaderRIOT, ReaderRIOTFactory}
import org.apache.jena.sparql.util.Context

import java.io.{InputStream, Reader}

object JellyReaderFactory extends ReaderRIOTFactory:
  override def create(language: Lang, profile: ParserProfile) = JellyReader

// meh, TODO
object JellyReader extends ReaderRIOT:
  override def read(reader: Reader, baseURI: String, ct: ContentType, output: StreamRDF, context: Context): Unit =
    ???

  override def read(in: InputStream, baseURI: String, ct: ContentType, output: StreamRDF, context: Context): Unit =
    output.start()
    output.finish()

object RDFReaderJelly extends RDFReaderRIOT(JellyLanguage.strLangJelly)
