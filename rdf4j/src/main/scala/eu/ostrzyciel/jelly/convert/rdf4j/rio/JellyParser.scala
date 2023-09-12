package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamFrame
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser

import java.io.{InputStream, Reader}

final class JellyParser extends AbstractRDFParser:
  private val decoder = Rdf4jConverterFactory.anyStatementDecoder

  override def getRDFFormat = JELLY

  override def parse(in: InputStream, baseURI: String): Unit =
    if (in == null) throw new IllegalArgumentException("Input stream must not be null")
    rdfHandler.startRDF()
    try {
      while in.available() > 0 do
        val frame = RdfStreamFrame.parseDelimitedFrom(in)
        frame match
          case Some(f) =>
            for row <- f.rows do
              decoder.ingestRow(row) match
                case Some(st) => rdfHandler.handleStatement(st)
                case None => ()
          case None => ()
    }
    finally {
      rdfHandler.endRDF()
    }

  override def parse(reader: Reader, baseURI: String): Unit = throw new UnsupportedOperationException
