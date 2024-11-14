package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import org.eclipse.rdf4j.rio.WriterConfig
import org.eclipse.rdf4j.rio.helpers.*

object JellyWriterSettings:
  def configFromOptions(frameSize: Long): WriterConfig =
    val c = new WriterConfig()
    c.set(FRAME_SIZE, frameSize)
    c

  def configFromOptions(opt: RdfStreamOptions, frameSize: Long = 256L): WriterConfig =
    val c = new WriterConfig()
    c.set(FRAME_SIZE, frameSize)
    c.set(STREAM_NAME, opt.streamName)
    c.set(PHYSICAL_TYPE, opt.physicalType)
    c.set(ALLOW_GENERALIZED_STATEMENTS, opt.generalizedStatements)
    c.set(ALLOW_RDF_STAR, opt.rdfStar)
    c.set(MAX_NAME_TABLE_SIZE, opt.maxNameTableSize.toLong)
    c.set(MAX_PREFIX_TABLE_SIZE, opt.maxPrefixTableSize.toLong)
    c.set(MAX_DATATYPE_TABLE_SIZE, opt.maxDatatypeTableSize.toLong)
    c
  
  val FRAME_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.frameSize",
    "Target RDF stream frame size. Frame size may be slightly larger than this value, " +
      "to fit the entire statement and its lookup entries in one frame.",
    256L
  )

  val STREAM_NAME = new StringRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.streamName",
    "Stream name",
    ""
  )

  val PHYSICAL_TYPE = new ClassRioSetting[PhysicalStreamType](
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.physicalType",
    "Physical stream type",
    PhysicalStreamType.QUADS
  )

  val ALLOW_GENERALIZED_STATEMENTS = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowGeneralizedStatements",
    "Allow generalized statements. Enabled by default, because we cannot know this in advance. " +
      "If your data does not contain generalized statements, it is recommended that you set this to false.",
    true
  )
  
  val ALLOW_RDF_STAR = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowRdfStar",
    "Allow RDF-star statements. Enabled by default, because we cannot know this in advance. " +
      "If your data does not contain RDF-star statements, it is recommended that you set this to false.",
    true
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
