package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.convert.rdf4j.Rdf4jConverterFactory
import eu.ostrzyciel.jelly.core.IoUtils
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

  /**
   * Read Jelly RDF data from an InputStream.
   * Automatically detects whether the input is a single frame (non-delimited) or a stream of frames (delimited).
   */
  override def parse(in: InputStream, baseURI: String): Unit =
    if (in == null) throw new IllegalArgumentException("Input stream must not be null")

    val config = getParserConfig
    val decoder = Rdf4jConverterFactory.anyStatementDecoder(
      Some(RdfStreamOptions(
        generalizedStatements = config.get(ALLOW_GENERALIZED_STATEMENTS).booleanValue(),
        rdfStar = config.get(ALLOW_RDF_STAR).booleanValue(),
        maxNameTableSize = config.get(MAX_NAME_TABLE_SIZE).toInt,
        maxPrefixTableSize = config.get(MAX_PREFIX_TABLE_SIZE).toInt,
        maxDatatypeTableSize = config.get(MAX_DATATYPE_TABLE_SIZE).toInt,
        version = config.get(PROTO_VERSION).toInt,
      )),
      namespaceHandler = (name, iri) => rdfHandler.handleNamespace(name, iri.stringValue())
    )
    inline def processFrame(f: RdfStreamFrame): Unit =
      for row <- f.rows do
        decoder.ingestRow(row) match
          case Some(st) => rdfHandler.handleStatement(st)
          case None => ()

    rdfHandler.startRDF()
    try {
      IoUtils.autodetectDelimiting(in) match
        case (false, newIn) =>
          // Non-delimited Jelly file
          // In this case, we can only read one frame
          val frame = RdfStreamFrame.parseFrom(newIn)
          processFrame(frame)
        case (true, newIn) =>
          // Delimited Jelly file
          // In this case, we can read multiple frames
          Iterator.continually(RdfStreamFrame.parseDelimitedFrom(newIn))
            .takeWhile(_.isDefined)
            .foreach { maybeFrame => processFrame(maybeFrame.get) }
    }
    finally {
      rdfHandler.endRDF()
    }

  override def parse(reader: Reader, baseURI: String): Unit = throw new UnsupportedOperationException
