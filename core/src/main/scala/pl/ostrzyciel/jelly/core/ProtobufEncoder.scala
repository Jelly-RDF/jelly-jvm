package pl.ostrzyciel.jelly.core

import pl.ostrzyciel.jelly.core.proto.*

import java.math.BigInteger
import scala.annotation.targetName
import scala.collection.mutable.ListBuffer

/**
 * Stateful encoder of a protobuf RDF stream.
 * @param options options for this stream
 */
abstract class ProtobufEncoder[TNode >: Null <: AnyRef, TTriple, TQuad](val options: StreamOptions):
  type TripleOrQuad = TTriple | TQuad

  // *** 1. THE PUBLIC INTERFACE ***
  // *******************************
  /**
   * Add an RDF triple statement to the stream.
   * @param triple triple to add
   * @return iterable of stream rows
   */
  final def addTriple(triple: TTriple): Iterable[RdfStreamRow] =
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
  final def addQuad(quad: TQuad): Iterable[RdfStreamRow] =
    handleHeader()
    val mainRow = RdfStreamRow(RdfStreamRow.Row.Quad(
      quadToProto(quad)
    ))
    extraRowsBuffer.append(mainRow)


  // *** 2. METHODS TO IMPLEMENT ***
  // *******************************
  protected def getS(triple: TTriple): TNode
  protected def getP(triple: TTriple): TNode
  protected def getO(triple: TTriple): TNode

  // @targetName used to get around method overloading with type erasure
  @targetName("getQuadS")
  protected def getS(quad: TQuad): TNode
  @targetName("getQuadP")
  protected def getP(quad: TQuad): TNode
  @targetName("getQuadO")
  protected def getO(quad: TQuad): TNode
  protected def getG(quad: TQuad): TNode

  /**
   * Turn an RDF node into its protobuf representation (or None in case of error)
   *
   * Use the protected final inline make* methods in this class to create the nodes.
   *
   * @param node RDF node
   * @return option of RdfTerm
   */
  protected def nodeToProto(node: TNode): Option[RdfTerm]


  // *** 3. THE PROTECTED INTERFACE ***
  // **********************************
  protected final inline def makeIriNode(iri: String): Some[RdfTerm] =
    val iriEnc = iriEncoder.encodeIri(iri)
    Some(RdfTerm(RdfTerm.Term.Iri(iriEnc)))

  protected final inline def makeBlankNode(label: String): Some[RdfTerm] =
    Some(RdfTerm(RdfTerm.Term.Bnode(RdfBnode(label))))

  protected final inline def makeSimpleLiteral(lex: String): Some[RdfTerm] =
    Some(RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, simpleLiteral)
    )))

  protected final inline def makeLangLiteral(lex: String, lang: String): Some[RdfTerm] =
    Some(RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, RdfLiteral.LiteralKind.Langtag(lang))
    )))

  protected final inline def makeDtLiteral(lex: String, dt: String): Some[RdfTerm] =
    Some(RdfTerm(RdfTerm.Term.Literal(
      RdfLiteral(lex, iriEncoder.encodeDatatype(dt))
    )))

  protected final inline def makeTripleNode(triple: TTriple): Some[RdfTerm] =
    Some(RdfTerm(RdfTerm.Term.TripleTerm(tripleToProto(triple))))


  // *** 4. PRIVATE FIELDS AND METHODS ***
  // *************************************
  private val extraRowsBuffer = new ListBuffer[RdfStreamRow]()
  private val iriEncoder = new NameEncoder(options, extraRowsBuffer)
  private var emittedCompressionOptions = false

  private val lastSubject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastPredicate: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastObject: LastNodeHolder[TNode] = new LastNodeHolder()
  private val lastGraph: LastNodeHolder[TNode] = new LastNodeHolder()

  private val termRepeat = Some(RdfTerm(RdfTerm.Term.Repeat(RdfRepeat())))
  private val simpleLiteral = RdfLiteral.LiteralKind.Simple(true)

  private def nodeToProtoWrapped(node: TNode, lastNodeHolder: LastNodeHolder[TNode]): Option[RdfTerm] =
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
      s = nodeToProtoWrapped(getS(triple), lastSubject),
      p = nodeToProtoWrapped(getP(triple), lastPredicate),
      o = nodeToProtoWrapped(getO(triple), lastObject),
    )

  private def quadToProto(quad: TQuad): RdfQuad =
    RdfQuad(
      s = nodeToProtoWrapped(getS(quad), lastSubject),
      p = nodeToProtoWrapped(getP(quad), lastPredicate),
      o = nodeToProtoWrapped(getO(quad), lastObject),
      g = nodeToProtoWrapped(getG(quad), lastGraph),
    )

  private def handleHeader(): Unit =
    extraRowsBuffer.clear()
    if !emittedCompressionOptions then
      emittedCompressionOptions = true
      extraRowsBuffer.append(
        RdfStreamRow(RdfStreamRow.Row.Options(options.toProto))
      )


