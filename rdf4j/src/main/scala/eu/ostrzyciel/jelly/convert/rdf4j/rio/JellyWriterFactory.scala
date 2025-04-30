package eu.ostrzyciel.jelly.convert.rdf4j.rio

import org.eclipse.rdf4j.rio.{RDFFormat, RDFWriter, RDFWriterFactory}

import java.io.{OutputStream, Writer}

final class JellyWriterFactory extends RDFWriterFactory:
  override def getRDFFormat: RDFFormat = JELLY

  override def getWriter(out: OutputStream): JellyWriter = JellyWriter(out)

  override def getWriter(out: OutputStream, baseURI: String): JellyWriter = getWriter(out)

  override def getWriter(writer: Writer): RDFWriter = throw new UnsupportedOperationException

  override def getWriter(writer: Writer, baseURI: String): RDFWriter = throw new UnsupportedOperationException
