package eu.ostrzyciel.jelly.convert.jena.patch

import org.apache.jena.graph.Node
import org.apache.jena.rdfpatch.RDFChanges

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

final class JenaChangesCollector extends RDFChanges:
  import JenaChangesCollector.*

  private val changes: ListBuffer[JenaChangesItem] = ListBuffer.empty

  def getChanges: Seq[JenaChangesItem] =
    changes.toSeq

  def size: Int =
    changes.size

  def replay(c: RDFChanges): Unit =
    c.start()
    getChanges.foreach(_.apply(c))
    c.finish()

  def header(field: String, value: Node): Unit =
    changes += Header(field, value)

  def add(g: Node, s: Node, p: Node, o: Node): Unit =
    changes += Add(g, s, p, o)

  def delete(g: Node, s: Node, p: Node, o: Node): Unit =
    changes += Delete(g, s, p, o)

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
