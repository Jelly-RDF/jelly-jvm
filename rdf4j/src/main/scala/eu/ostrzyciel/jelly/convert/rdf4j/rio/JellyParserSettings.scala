package eu.ostrzyciel.jelly.convert.rdf4j.rio

import eu.ostrzyciel.jelly.core.JellyOptions
import eu.ostrzyciel.jelly.core.proto.v1.RdfStreamOptions
import org.eclipse.rdf4j.rio.ParserConfig
import org.eclipse.rdf4j.rio.helpers.*

object JellyParserSettings:
  val defaultOptions: RdfStreamOptions = JellyOptions.defaultSupportedOptions

  def configFromOptions(opt: RdfStreamOptions): ParserConfig =
    val c = new ParserConfig()
    c.set(PROTO_VERSION, opt.version.toLong)
    c.set(ALLOW_GENERALIZED_STATEMENTS, opt.generalizedStatements)
    c.set(ALLOW_RDF_STAR, opt.rdfStar)
    c.set(MAX_NAME_TABLE_SIZE, opt.maxNameTableSize.toLong)
    c.set(MAX_PREFIX_TABLE_SIZE, opt.maxPrefixTableSize.toLong)
    c.set(MAX_DATATYPE_TABLE_SIZE, opt.maxDatatypeTableSize.toLong)
    c

  val PROTO_VERSION = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.protoVersion",
    "Maximum supported Jelly protocol version",
    defaultOptions.version.toLong
  )

  val ALLOW_GENERALIZED_STATEMENTS = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowGeneralizedStatements",
    "Allow decoding generalized statements",
    defaultOptions.generalizedStatements
  )

  val ALLOW_RDF_STAR = new BooleanRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.allowRdfStar",
    "Allow decoding RDF-star statements",
    defaultOptions.rdfStar
  )

  val MAX_NAME_TABLE_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.maxNameTableSize",
    "Maximum size of the name table",
    defaultOptions.maxNameTableSize.toLong
  )

  val MAX_PREFIX_TABLE_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.maxPrefixTableSize",
    "Maximum size of the prefix table",
    defaultOptions.maxPrefixTableSize.toLong
  )

  val MAX_DATATYPE_TABLE_SIZE = new LongRioSetting(
    "eu.ostrzyciel.jelly.convert.rdf4j.rio.maxDatatypeTableSize",
    "Maximum size of the datatype table",
    defaultOptions.maxDatatypeTableSize.toLong
  )

