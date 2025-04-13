package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStatementType
import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.RDFChanges
import org.apache.jena.sparql.core.Quad

import scala.collection.mutable.ListBuffer

object JenaChangesCollector:
  sealed trait JenaChangesItem:
    def apply(c: RDFChanges): Unit
  final case class Header(field: String, value: Node) extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.header(field, value)
  final case class Add(g: Node, s: Node, p: Node, o: Node) extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.add(g, s, p, o)
  final case class Delete(g: Node, s: Node, p: Node, o: Node) extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.delete(g, s, p, o)
  final case class AddPrefix(gn: Node, prefix: String, uriStr: String) extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.addPrefix(gn, prefix, uriStr)
  final case class DeletePrefix(gn: Node, prefix: String) extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.deletePrefix(gn, prefix)
  case object TxnBegin extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.txnBegin()
  case object TxnCommit extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.txnCommit()
  case object TxnAbort extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.txnAbort()
  case object Segment extends JenaChangesItem:
    def apply(c: RDFChanges): Unit = c.segment()

/**
 * Helper class to collect and replay items from Jena RDFChanges streams.
 * @param stType The statement type of the Jelly-Patch stream.
 */
final class JenaChangesCollector(stType: PatchStatementType) extends RDFChanges:
  import JenaChangesCollector.*

  private val changes: ListBuffer[JenaChangesItem] = ListBuffer.empty

  def getChanges: Seq[JenaChangesItem] =
    changes.toSeq

  def size: Int =
    changes.size

  def replay(c: RDFChanges, callStartFinish: Boolean): Unit =
    if callStartFinish then c.start()
    getChanges.foreach(_.apply(c))
    if callStartFinish then c.finish()

  def header(field: String, value: Node): Unit =
    changes += Header(field, value)

  def add(g: Node, s: Node, p: Node, o: Node): Unit =
    changes += Add(coerceGraph(g), s, p, o)

  def delete(g: Node, s: Node, p: Node, o: Node): Unit =
    changes += Delete(coerceGraph(g), s, p, o)

  private def coerceGraph(g: Node): Node =
    if g == null && stType.isQuads then Quad.defaultGraphNodeGenerated
    else if stType.isTriples then null
    else g

  def addPrefix(gn: Node, prefix: String, uriStr: String): Unit =
    changes += AddPrefix(gn, prefix, uriStr)

  def deletePrefix(gn: Node, prefix: String): Unit =
    changes += DeletePrefix(gn, prefix)

  def txnBegin(): Unit =
    changes += TxnBegin

  def txnCommit(): Unit =
    changes += TxnCommit

  def txnAbort(): Unit =
    changes += TxnAbort

  def segment(): Unit =
    changes += Segment

  def start(): Unit = ()

  def finish(): Unit = ()
