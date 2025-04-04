package eu.ostrzyciel.jelly.core

import eu.ostrzyciel.jelly.core.internal.*
import eu.ostrzyciel.jelly.core.proto.v1.*

import scala.collection.mutable

object ProtoEncoder:
  /**
   * Parameters passed to the Jelly encoder.
   *
   * New fields may be added in the future, but always with a default value and in a sequential order.
   * However, it is still recommended to use named arguments when creating this object.
   *
   * @param options options for this stream (required)
   * @param enableNamespaceDeclarations whether to allow namespace declarations in the stream.
   *                                    If true, this will raise the stream version to 2 (Jelly 1.1.0). Otherwise,
   *                                    the stream version will be 1 (Jelly 1.0.0).
   * @param maybeRowBuffer              optional buffer for storing stream rows that should go into a stream frame.
   *                                    If provided, the encoder will append the rows to this buffer instead of
   *                                    returning them, so methods like `addTripleStatement` will return Seq().
   */
  final case class Params(
    options: RdfStreamOptions,
    enableNamespaceDeclarations: Boolean = false,
    maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]] = None,
  )

/**
 * Base trait for RDF stream encoders.
 * @tparam TNode type of RDF nodes in the library
 * @tparam TTriple type of triple statements in the library
 * @tparam TQuad type of quad statements in the library
 * @tparam TQuoted Unused since 2.7.0. Can be set to `Nothing` or `?`.
 */
trait ProtoEncoder[TNode, -TTriple, -TQuad, -TQuoted]
  extends ProtoEncoderBase[TNode, TTriple, TQuad] with RowBufferAppender:

  /**
   * RdfStreamOptions for this encoder.
   */
  val options: RdfStreamOptions

  /**
   * Whether namespace declarations are enabled for this encoder.
   */
  val enableNamespaceDeclarations: Boolean

  /**
   * Buffer for storing stream rows that should go into a stream frame.
   */
  val maybeRowBuffer: Option[mutable.Buffer[RdfStreamRow]]

  /**
   * Add an RDF triple statement to the stream.
   *
   * If your library does not support quad objects, use `addTripleStatement(s, p, o)` instead.
   *
   * @param triple triple to add
   * @return iterable of stream rows
   * @throws RdfProtoSerializationError if the library does not support triple objects or
   *                                    if a serialization error occurs.
   */
  final def addTripleStatement(triple: TTriple): Iterable[RdfStreamRow] =
    addTripleStatement(
      converter.getTstS(triple),
      converter.getTstP(triple),
      converter.getTstO(triple)
    )

  /**
   * Add an RDF triple statement to the stream.
   * @param subject subject
   * @param predicate predicate
   * @param `object` object
   * @return iterable of stream rows
   * @since 2.9.0
   * @throws RdfProtoSerializationError if a serialization error occurs
   */
  def addTripleStatement(subject: TNode, predicate: TNode, `object`: TNode): Iterable[RdfStreamRow]

  /**
   * Add an RDF quad statement to the stream.
   *
   * If your library does not support quad objects, use `addQuadStatement(s, p, o, g)` instead.
   *
   * @param quad quad to add
   * @return iterable of stream rows
   * @throws RdfProtoSerializationError if the library does not support quad objects or
   *                                    if a serialization error occurs.
   */
  final def addQuadStatement(quad: TQuad): Iterable[RdfStreamRow] =
    addQuadStatement(
      converter.getQstS(quad),
      converter.getQstP(quad),
      converter.getQstO(quad),
      converter.getQstG(quad)
    )

  /**
   * Add an RDF quad statement to the stream.
   *
   * @param subject subject
   * @param predicate predicate
   * @param `object` object
   * @param graph graph
   * @return iterable of stream rows
   * @since 2.9.0
   * @throws RdfProtoSerializationError if a serialization error occurs
   */
  def addQuadStatement(subject: TNode, predicate: TNode, `object`: TNode, graph: TNode): Iterable[RdfStreamRow]

  /**
   * Signal the start of a new (named) delimited graph in a GRAPHS stream.
   * Null value is interpreted as the default graph.
   *
   * @param graph graph node
   * @return iterable of stream rows
   * @throws RdfProtoSerializationError if a serialization error occurs
   */
  def startGraph(graph: TNode): Iterable[RdfStreamRow]

  /**
   * Signal the start of the default delimited graph in a GRAPHS stream.
   *
   * @return iterable of stream rows
   * @throws RdfProtoSerializationError if a serialization error occurs
   */
  def startDefaultGraph(): Iterable[RdfStreamRow]

  /**
   * Signal the end of a delimited graph in a GRAPHS stream.
   *
   * @return iterable of stream rows (always of length 1)
   * @throws RdfProtoSerializationError if a serialization error occurs
   */
  def endGraph(): Iterable[RdfStreamRow]

  /**
   * Declare a namespace in the stream.
   * This is equivalent to the PREFIX directive in Turtle.
   *
   * @param name     short name of the namespace (without the colon)
   * @param iriValue IRI of the namespace
   * @return iterable of stream rows
   * @throws RdfProtoSerializationError if a serialization error occurs
   */
  def declareNamespace(name: String, iriValue: String): Iterable[RdfStreamRow]
