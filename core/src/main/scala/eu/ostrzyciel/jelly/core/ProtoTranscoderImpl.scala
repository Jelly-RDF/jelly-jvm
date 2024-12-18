package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.annotation.switch
import scala.collection.mutable.ArrayBuffer

// Note: the caller is responsible for setting a physical stream type in the output!

private final class ProtoTranscoderImpl(
  supportedInputOptions: Option[RdfStreamOptions],
  outputOptions: RdfStreamOptions
) extends ProtoTranscoder:
  override def ingestRow(row: RdfStreamRow): Iterable[RdfStreamRow] =
    rowBuffer.clear()
    processRow(row)
    rowBuffer.toSeq

  override def ingestFrame(frame: RdfStreamFrame): RdfStreamFrame =
    rowBuffer.clear()
    for row <- frame.rows do
      processRow(row)
    val newFrame = RdfStreamFrame(rowBuffer.toSeq)
    newFrame

  // Transcoder state
  private val checkInputOptions = supportedInputOptions.isDefined
  private val prefixLookup: TranscoderLookup = new TranscoderLookup(false, outputOptions.maxPrefixTableSize)
  private val nameLookup: TranscoderLookup = new TranscoderLookup(true, outputOptions.maxNameTableSize)
  private val datatypeLookup: TranscoderLookup = new TranscoderLookup(false, outputOptions.maxDatatypeTableSize)

  // Current input stream state
  private var inputOptions: RdfStreamOptions = null
  private var inputUsesPrefixes = false

  // Current output stream state
  private val rowBuffer = new ArrayBuffer[RdfStreamRow](128)
  private var changeInTerms = false
  private var emittedOptions = false

  private def processRow(row: RdfStreamRow): Unit =
    val r = row.row
    if r == null then
      throw new RdfProtoTranscodingError("Row kind is not set.")
    (r.streamRowValueNumber: @switch) match
      case RdfStreamRow.OPTIONS_FIELD_NUMBER => handleOptions(r.options)
      case RdfStreamRow.TRIPLE_FIELD_NUMBER => handleTriple(row)
      case RdfStreamRow.QUAD_FIELD_NUMBER => handleQuad(row)
      case RdfStreamRow.GRAPH_START_FIELD_NUMBER =>
        this.changeInTerms = false
        val term = r.graphStart.graph
        val g1 = handleGraphTerm(term)
        if changeInTerms then
          rowBuffer.append(RdfStreamRow(RdfGraphStart(g1)))
        else rowBuffer.append(row)
      case RdfStreamRow.GRAPH_END_FIELD_NUMBER => rowBuffer.append(row)
      case RdfStreamRow.NAME_FIELD_NUMBER =>
        val name = r.name
        val entry = nameLookup.addEntry(name.id, name.value)
        if !entry.newEntry then ()
        else if entry.setId == name.id then rowBuffer.append(row)
        else rowBuffer.append(RdfStreamRow(RdfNameEntry(entry.setId, name.value)))
      case RdfStreamRow.PREFIX_FIELD_NUMBER =>
        val prefix = r.prefix
        val entry = prefixLookup.addEntry(prefix.id, prefix.value)
        if !entry.newEntry then ()
        else if entry.setId == prefix.id then rowBuffer.append(row)
        else rowBuffer.append(RdfStreamRow(RdfPrefixEntry(entry.setId, prefix.value)))
      case RdfStreamRow.DATATYPE_FIELD_NUMBER =>
        val datatype = r.datatype
        val entry = datatypeLookup.addEntry(datatype.id, datatype.value)
        if !entry.newEntry then ()
        else if entry.setId == datatype.id then rowBuffer.append(row)
        else rowBuffer.append(RdfStreamRow(RdfDatatypeEntry(entry.setId, datatype.value)))
      case _ =>
        // This case should never happen
        throw new RdfProtoTranscodingError("Row kind is not set.")

  private def handleTriple(row: RdfStreamRow): Unit =
    this.changeInTerms = false
    val triple = row.row.triple
    val s1 = handleSpoTerm(triple.subject)
    val p1 = handleSpoTerm(triple.predicate)
    val o1 = handleSpoTerm(triple.`object`)
    if changeInTerms then
      rowBuffer.append(RdfStreamRow(RdfTriple(s1, p1, o1)))
    else rowBuffer.append(row)

  private def handleQuad(row: RdfStreamRow): Unit =
    this.changeInTerms = false
    val quad = row.row.quad
    val s1 = handleSpoTerm(quad.subject)
    val p1 = handleSpoTerm(quad.predicate)
    val o1 = handleSpoTerm(quad.`object`)
    val g1 = handleGraphTerm(quad.graph)
    if changeInTerms then
      rowBuffer.append(RdfStreamRow(RdfQuad(s1, p1, o1, g1)))
    else rowBuffer.append(row)

  private def handleSpoTerm(term: SpoTerm): SpoTerm =
    if term == null then null
    else if term.isIri then handleIri(term.iri)
    else if term.isBnode then term
    else if term.isLiteral then handleLiteral(term.literal)
    else if term.isTripleTerm then handleTripleTerm(term.tripleTerm)
    else throw new RdfProtoTranscodingError("Unknown term type.")

  private def handleGraphTerm(term: GraphTerm): GraphTerm =
    if term == null then null
    else if term.isIri then handleIri(term.iri)
    else if term.isDefaultGraph then term
    else if term.isBnode then term
    else if term.isLiteral then handleLiteral(term.literal)
    else throw new RdfProtoTranscodingError("Unknown term type.")

  private def handleIri(iri: RdfIri): RdfIri =
    val prefix = iri.prefixId
    val name = iri.nameId
    val prefix1 = if inputUsesPrefixes then prefixLookup.remap(prefix) else 0
    val name1 = nameLookup.remap(name)
    if prefix1 != prefix || name1 != name then
      changeInTerms = true
      RdfIri(prefix1, name1)
    else iri

  private def handleLiteral(literal: RdfLiteral): RdfLiteral =
    if !literal.literalKind.isDatatype then literal
    else
      val dt = literal.literalKind.datatype
      val dt1 = datatypeLookup.remap(dt)
      if dt1 != dt then
        changeInTerms = true
        RdfLiteral(literal.lex, RdfLiteral.LiteralKind.Datatype(dt1))
      else literal

  private def handleTripleTerm(triple: RdfTriple): RdfTriple =
    val s1 = handleSpoTerm(triple.subject)
    val p1 = handleSpoTerm(triple.predicate)
    val o1 = handleSpoTerm(triple.`object`)
    if s1 != triple.subject || p1 != triple.predicate || o1 != triple.`object` then
      changeInTerms = true
      RdfTriple(s1, p1, o1)
    else triple

  private def handleOptions(options: RdfStreamOptions): Unit =
    if checkInputOptions then
      if outputOptions.physicalType != options.physicalType then
        throw new RdfProtoTranscodingError("Input stream has a different physical type than the output. " +
          f"Input: ${options.physicalType} output: ${outputOptions.physicalType}")
      JellyOptions.checkCompatibility(options, supportedInputOptions.get)
    this.inputUsesPrefixes = options.maxPrefixTableSize > 0
    if inputUsesPrefixes then
      prefixLookup.newInputStream(options.maxPrefixTableSize)
    else if outputOptions.maxPrefixTableSize > 0 then
      throw new RdfProtoTranscodingError("Output stream uses prefixes, but the input stream does not.")
    nameLookup.newInputStream(options.maxNameTableSize)
    datatypeLookup.newInputStream(options.maxDatatypeTableSize)
    // Update the input options
    inputOptions = options
    if !emittedOptions then
      emittedOptions = true
      rowBuffer.append(RdfStreamRow(outputOptions.copy(
        version = Constants.protoVersion
      )))
