package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.core.proto.v1.{RdfStreamOptions, RdfStreamType}
import org.eclipse.rdf4j.rio.WriterConfig
import org.eclipse.rdf4j.rio.helpers.*

object JellyWriterSettings:
  def configFromOptions(opt: RdfStreamOptions, frameSize: Long = 256L): WriterConfig =
    val c = new WriterConfig()
    c.set(FRAME_SIZE, frameSize)
    c.set(STREAM_NAME, opt.streamName)
    c.set(STREAM_TYPE, opt.streamType)
    c.set(ALLOW_GENERALIZED_STATEMENTS, opt.generalizedStatements)
    c.set(USE_REPEAT, opt.useRepeat)
    c.set(MAX_NAME_TABLE_SIZE, opt.maxNameTableSize.toLong)
    c.set(MAX_PREFIX_TABLE_SIZE, opt.maxPrefixTableSize.toLong)
    c.set(MAX_DATATYPE_TABLE_SIZE, opt.maxDatatypeTableSize.toLong)
    c
  
  val FRAME_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.frameSize",
    "Target RDF frame size",
    256L
  )

  val STREAM_NAME = new StringRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.streamName",
    "Stream name",
    ""
  )

  val STREAM_TYPE = new ClassRioSetting[RdfStreamType](
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.streamType",
    "Stream type",
    RdfStreamType.RDF_STREAM_TYPE_TRIPLES
  )

  val ALLOW_GENERALIZED_STATEMENTS = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowGeneralizedStatements",
    "Allow generalized statements",
    false
  )

  val USE_REPEAT = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.useRepeat",
    "Whether to compress repeating values (recommended)",
    true
  )
  
  val ALLOW_RDF_STAR = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowRdfStar",
    "Allow RDF-star statements",
    false
  )

  val MAX_NAME_TABLE_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.maxNameTableSize",
    "Maximum size of the name table",
    128L
  )

  val MAX_PREFIX_TABLE_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.maxPrefixTableSize",
    "Maximum size of the prefix table",
    16L
  )

  val MAX_DATATYPE_TABLE_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.maxDatatypeTableSize",
    "Maximum size of the datatype table",
    16L
  )
