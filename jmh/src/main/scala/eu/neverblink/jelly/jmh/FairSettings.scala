package eu.neverblink.jelly.jmh

import org.apache.jena.query.ARQ
import org.apache.jena.riot.{RDFParserBuilder, RDFWriterBuilder, SysRIOT}
import org.apache.jena.riot.lang.LabelToNode
import org.apache.jena.sparql.SystemARQ
import org.apache.jena.sparql.util.Context
import org.apache.jena.sys.JenaSystem
import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}
import org.eclipse.rdf4j.query.resultio.{QueryResultParser, QueryResultWriter}
import org.eclipse.rdf4j.rio.helpers.{BasicParserSettings, BasicWriterSettings}
import org.eclipse.rdf4j.rio.{DatatypeHandler, LanguageHandler, RDFParser, WriterConfig}

/** Parser and writer settings that try to make every format do the same work as far as possible.
  *
  * Every reader keeps blank node labels as given, skips validating of IRIs, language tags and
  * datatypes, and does not decode special / library specific RDF terms.
  */
object FairSettings:

  /** Whether the settings below are applied at all. */
  val enabled: Boolean = !java.lang.Boolean.getBoolean("jelly.bench.defaults")

  private val defaultRdf4jHandlers: Boolean =
    sys.props.get("jelly.bench.rdf4jHandlers").contains("default")

  /** Must be called after Jena's own initialization, which turns CDT literals on. */
  def initJena(): Unit =
    JenaSystem.init()
    // CDT literals (composite datatypes) are a Jena extension: with them on, every typed literal
    // is checked against the CDT datatypes
    if enabled then SystemARQ.EnableCDTs = false

  def jenaParser(builder: RDFParserBuilder): RDFParserBuilder =
    if enabled then
      builder
        .checking(false)
        // What Jena's Thrift and Protobuf readers do
        .labelToNode(LabelToNode.createUseLabelAsGiven())
    else builder

  /** For Jena's writers that start from a whole graph or dataset. */
  def jenaWriter(builder: RDFWriterBuilder): RDFWriterBuilder =
    if enabled then
      // The RDF/XML writer checks every IRI it writes by default
      builder.set(SysRIOT.sysRdfWriterProperties, java.util.Map.of("allowBadURIs", "true"))
    else builder

  /** The context for Jena's result set readers and writers, null for the default one. */
  def jenaRowSetContext: Context | Null =
    if enabled then
      val context = ARQ.getContext.copy()
      context.set(ARQ.inputGraphBNodeLabels, true)
      context.set(ARQ.outputGraphBNodeLabels, true)
      context
    else null

  /** Accepts every datatype, so that RDF4J neither looks it up nor reports it as unknown. */
  private object AnyDatatype extends DatatypeHandler:
    override def isRecognizedDatatype(datatype: IRI): Boolean = true
    override def verifyDatatype(value: String, datatype: IRI): Boolean = true
    override def normalizeDatatype(value: String, datatype: IRI, vf: ValueFactory): Literal =
      vf.createLiteral(value, datatype)
    override def getKey: String = "jelly-bench-any"

  /** Accepts every language tag, see [[AnyDatatype]]. */
  private object AnyLanguage extends LanguageHandler:
    override def isRecognizedLanguage(tag: String): Boolean = true
    override def verifyLanguage(value: String, tag: String): Boolean = true
    override def normalizeLanguage(value: String, tag: String, vf: ValueFactory): Literal =
      vf.createLiteral(value, tag)
    override def normalizeLanguage(
        value: String,
        tag: String,
        dir: Literal.BaseDirection,
        vf: ValueFactory,
    ): Literal = vf.createLiteral(value, tag, dir)
    override def getKey: String = "jelly-bench-any"

  /** Kept blank node labels are the one setting the harness always had. */
  def rdf4jParser(parser: RDFParser): Unit =
    parser.set(BasicParserSettings.PRESERVE_BNODE_IDS, true)
    if enabled then
      parser.set(BasicParserSettings.PROCESS_ENCODED_TRIPLE_TERMS, false)
      parser.set(BasicParserSettings.VERIFY_URI_SYNTAX, false)
      parser.set(BasicParserSettings.VERIFY_RELATIVE_URIS, false)
      parser.set(BasicParserSettings.VERIFY_LANGUAGE_TAGS, false)
      parser.set(BasicParserSettings.NORMALIZE_LANGUAGE_TAGS, false)
      parser.set(BasicParserSettings.FAIL_ON_UNKNOWN_LANGUAGES, false)
      parser.set(BasicParserSettings.VERIFY_DATATYPE_VALUES, false)
      parser.set(BasicParserSettings.NORMALIZE_DATATYPE_VALUES, false)
      parser.set(BasicParserSettings.FAIL_ON_UNKNOWN_DATATYPES, false)
      if !defaultRdf4jHandlers then
        parser.set(BasicParserSettings.DATATYPE_HANDLERS, java.util.List.of(AnyDatatype))
        parser.set(BasicParserSettings.LANGUAGE_HANDLERS, java.util.List.of(AnyLanguage))

  /** Call before setting the result handler: RDF4J reads the triple term setting right then. */
  def rdf4jResultParser(parser: QueryResultParser): Unit =
    if enabled then parser.set(BasicParserSettings.PROCESS_ENCODED_TRIPLE_TERMS, false)

  def rdf4jWriter(config: WriterConfig): WriterConfig =
    if enabled then
      config
        .set(BasicWriterSettings.ENCODE_TRIPLE_TERMS, false)
        .set(BasicWriterSettings.ANNOUNCE_RDF12_VERSION, false)
        .set(BasicWriterSettings.CONVERT_RDF_12_REIFICATION, false)
        .set(BasicWriterSettings.PRETTY_PRINT, false)
        .set(BasicWriterSettings.INLINE_BLANK_NODES, false)
    else config

  def rdf4jResultWriter(writer: QueryResultWriter): Unit =
    if enabled then writer.getWriterConfig.set(BasicWriterSettings.ENCODE_TRIPLE_TERMS, false)
