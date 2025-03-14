package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.core.proto.v1.{PhysicalStreamType, RdfStreamOptions}
import org.eclipse.rdf4j.rio.WriterConfig
import org.eclipse.rdf4j.rio.helpers.*

object JellyWriterSettings:
  def configFromOptions(frameSize: Long): WriterConfig = configFromOptions(frameSize, false)

  def configFromOptions(frameSize: Long, enableNamespaceDeclarations: Boolean): WriterConfig =
    val c = new WriterConfig()
    c.set(FRAME_SIZE, frameSize)
    c.set(ENABLE_NAMESPACE_DECLARATIONS, enableNamespaceDeclarations)
    c

  def configFromOptions(
    opt: RdfStreamOptions, frameSize: Long = 256L, enableNamespaceDeclarations: Boolean = false
  ): WriterConfig =
    val c = new WriterConfig()
    c.set(FRAME_SIZE, frameSize)
    c.set(ENABLE_NAMESPACE_DECLARATIONS, enableNamespaceDeclarations)
    c.set(STREAM_NAME, opt.streamName)
    c.set(PHYSICAL_TYPE, opt.physicalType)
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

  val ENABLE_NAMESPACE_DECLARATIONS = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.enableNamespaceDeclarations",
    "Enable namespace declarations in the output (equivalent to PREFIX directives in Turtle syntax). " +
      "This option is disabled by default and is not recommended when your only concern is performance. " +
      "It is only useful when you want to preserve the namespace declarations in the output. " +
      "Enabling this causes the stream to be written in protocol version 2 (Jelly 1.1.0) instead of 1.",
    false
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

  @deprecated("Generalized statements are not supported by RDF4J Rio", "2.9.0")
  val ALLOW_GENERALIZED_STATEMENTS = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowGeneralizedStatements",
    "DEPRECATED since Jelly-JVM 2.9.0: Allow generalized statements. Disabled by default, because " +
      "RDF4J Rio does not really support generalized statements. This option was mistakenly " +
      "included in previous versions on the assumption that it does.",
    false
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
