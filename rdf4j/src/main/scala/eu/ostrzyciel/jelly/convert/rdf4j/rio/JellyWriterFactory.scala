package eu.ostrzyciel.jelly.convert.rdf4j.rio

import org.eclipse.rdf4j.rio.RDFWriterFactory

import java.io.{OutputStream, Writer}

final class JellyWriterFactory extends RDFWriterFactory:
  override def getRDFFormat = JELLY

  override def getWriter(out: OutputStream) = JellyWriter(out)

  override def getWriter(out: OutputStream, baseURI: String) = getWriter(out)

  override def getWriter(writer: Writer) = throw new UnsupportedOperationException

  override def getWriter(writer: Writer, baseURI: String) = throw new UnsupportedOperationException
