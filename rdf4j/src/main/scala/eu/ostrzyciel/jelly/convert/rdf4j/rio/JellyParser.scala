package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamFrame, RdfStreamOptions}
import org.eclipse.rdf4j.rio.{RDFFormat, RioSetting}
import org.eclipse.rdf4j.rio.helpers.AbstractRDFParser

import java.io.{InputStream, Reader}
import java.util

final class JellyParser extends AbstractRDFParser:
  import JellyParserSettings.*

  override def getRDFFormat: RDFFormat = JELLY

  override def getSupportedSettings: util.HashSet[RioSetting[_]] =
    val s = new util.HashSet[RioSetting[_]](super.getSupportedSettings)
    s.add(PROTO_VERSION)
    s.add(ALLOW_GENERALIZED_STATEMENTS)
    s.add(ALLOW_RDF_STAR)
    s.add(MAX_NAME_TABLE_SIZE)
    s.add(MAX_PREFIX_TABLE_SIZE)
    s.add(MAX_DATATYPE_TABLE_SIZE)
    s

  override def parse(in: InputStream, baseURI: String): Unit =
    if (in == null) throw new IllegalArgumentException("Input stream must not be null")

    val config = getParserConfig
    val decoder = Rdf4jConverterFactory.anyStatementDecoder(Some(RdfStreamOptions(
      generalizedStatements = config.get(ALLOW_GENERALIZED_STATEMENTS).booleanValue(),
      rdfStar = config.get(ALLOW_RDF_STAR).booleanValue(),
      maxNameTableSize = config.get(MAX_NAME_TABLE_SIZE).toInt,
      maxPrefixTableSize = config.get(MAX_PREFIX_TABLE_SIZE).toInt,
      maxDatatypeTableSize = config.get(MAX_DATATYPE_TABLE_SIZE).toInt,
      version = config.get(PROTO_VERSION).toInt,
    )))

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
