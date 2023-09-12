package eu.ostrzyciel.jelly.convert.rdf4j.rio

import org.eclipse.rdf4j.rio.{RDFFormat, RDFParserFactory}

final class JellyParserFactory extends RDFParserFactory:
  override def getRDFFormat: RDFFormat = JELLY

  override def getParser: JellyParser = JellyParser()
