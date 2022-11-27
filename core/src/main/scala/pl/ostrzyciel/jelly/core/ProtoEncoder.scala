package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.proto.*

import java.math.BigInteger
import scala.collection.mutable.ListBuffer

/**
 * Stateful encoder of a protobuf RDF stream.
 * @param options options for this stream
 */
abstract class ProtoEncoder[TNode >: Null <: AnyRef, TTriple, TQuad, TQuoted](val options: JellyOptions):
  // *** 1. THE PUBLIC INTERFACE ***
  // *******************************
  /**
   * Add an RDF triple statement to the stream.
   * @param triple triple to add
   * @return iterable of stream rows
   */
  final def addTripleStatement(triple: TTriple): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(RdfStreamRow.Row.Triple(
      tripleToProto(triple)
    ))
    extraRowsBuffer.append(mainRow)

  /**
   * Add an RDF quad statement to the stream.
   * @param quad quad to add
   * @return iterable of stream rows
   */
  final def addQuadStatement(quad: TQuad): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(RdfStreamRow.Row.Quad(
      quadToProto(quad)
    ))
    extraRowsBuffer.append(mainRow)


  // *** 2. METHODS TO IMPLEMENT ***
  // *******************************
  // Triple statement deconstruction
  protected def getTstS(triple: TTriple): TNode
  protected def getTstP(triple: TTriple): TNode
  protected def getTstO(triple: TTriple): TNode

  // Quad statement deconstruction
  protected def getQstS(quad: TQuad): TNode
  protected def getQstP(quad: TQuad): TNode
  protected def getQstO(quad: TQuad): TNode
  protected def getQstG(quad: TQuad): TNode

  // Quoted triple term deconstruction
  protected def getQuotedS(triple: TQuoted): TNode
  protected def getQuotedP(triple: TQuoted): TNode
  protected def getQuotedO(triple: TQuoted): TNode

  /**
   * Turn an RDF node into its protobuf representation.
   *
   * Use the protected final inline make* methods in this class to create the nodes.
   *
   * @param node RDF node
   * @return option of RdfTerm
   * @throws RdfProtoSerializationError if node cannot be encoded
   */
  protected def nodeToProto(node: TNode): RdfTerm


  // *** 3. THE PROTECTED INTERFACE ***
  // **********************************
  protected final inline def makeIriNode(iri: String): RdfTerm =
    val iriEnc = iriEncoder.encodeIri(iri)
    RdfTerm(RdfTerm.Term.Iri(iriEnc))

  protected final inline def makeBlankNode(label: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Bnode(RdfBnode(label)))

  protected final inline def makeSimpleLiteral(lex: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, simpleLiteral)
    ))

  protected final inline def makeLangLiteral(lex: String, lang: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))
    ))

  protected final inline def makeDtLiteral(lex: String, dt: String): RdfTerm =
    RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, iriEncoder.encodeDatatype(dt))
    ))

  protected final inline def makeTripleNode(triple: TQuoted): RdfTerm =
    RdfTerm(RdfTerm.Term.TripleTerm(quotedToProto(triple)))


  // *** 4. PRIVATE FIELDS AND METHODS ***
  // *************************************
  private val extraRowsBuffer = new ListBuffer[RdfStreamRow]()
  private val iriEncoder = new NameEncoder(options, extraRowsBuffer)
  private var emittedCompressionOptions = false

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  private val termRepeat = RdfTerm(RdfTerm.Term.Repeat(RdfRepeat()))
  private val simpleLiteral = RdfLiteral.LiteralKind.Simple(true)

  private def nodeToProtoWrapped(node: TNode, lastNodeHolder: LastNodeHolder[TNode]): RdfTerm =
    if options.useRepeat then
      lastNodeHolder.node match
        case oldNode if node == oldNode => termRepeat
        case _ =>
          lastNodeHolder.node = node
          nodeToProto(node)
    else
      nodeToProto(node)

  private def tripleToProto(triple: TTriple): RdfTriple =
    RdfTriple(
      s = nodeToProtoWrapped(getTstS(triple), lastSubject),
      p = nodeToProtoWrapped(getTstP(triple), lastPredicate),
      o = nodeToProtoWrapped(getTstO(triple), lastObject),
    )

  private def quadToProto(quad: TQuad): RdfQuad =
    RdfQuad(
      s = nodeToProtoWrapped(getQstS(quad), lastSubject),
      p = nodeToProtoWrapped(getQstP(quad), lastPredicate),
      o = nodeToProtoWrapped(getQstO(quad), lastObject),
      g = nodeToProtoWrapped(getQstG(quad), lastGraph),
    )

  private def quotedToProto(quoted: TQuoted): RdfTriple =
    // ! No RdfRepeat support for quoted triples.
    RdfTriple(
      s = nodeToProto(getQuotedS(quoted)),
      p = nodeToProto(getQuotedP(quoted)),
      o = nodeToProto(getQuotedO(quoted)),
    )

  private def handleHeader(): Unit =
    extraRowsBuffer.clear()
    if !emittedCompressionOptions then
      emittedCompressionOptions = true
      extraRowsBuffer.append(
        RdfStreamRow(RdfStreamRow.Row.Options(options.toProto))
      )


