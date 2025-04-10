package eu.ostrzyciel.jelly.core.patch.helpers

import eu.ostrzyciel.jelly.core.helpers.Mrl
import eu.ostrzyciel.jelly.core.patch.handler.AnyPatchHandler

import scala.annotation.experimental
import scala.collection.mutable

@experimental
final class PatchCollector extends AnyPatchHandler[Mrl.Node]:
  val statements: mutable.ListBuffer[Mpl.PatchStatement] = mutable.ListBuffer.empty

  def replayTo(handler: AnyPatchHandler[Mrl.Node]): Unit =
    statements.foreach(_.apply(handler))

  def addQuad(s: Mrl.Node, p: Mrl.Node, o: Mrl.Node, g: Mrl.Node): Unit =
    statements += Mpl.Add(Mrl.Quad(s, p, o, g))

  def deleteQuad(s: Mrl.Node, p: Mrl.Node, o: Mrl.Node, g: Mrl.Node): Unit =
    statements += Mpl.Delete(Mrl.Quad(s, p, o, g))

  def addTriple(s: Mrl.Node, p: Mrl.Node, o: Mrl.Node): Unit =
    statements += Mpl.Add(Mrl.Triple(s, p, o))

  def deleteTriple(s: Mrl.Node, p: Mrl.Node, o: Mrl.Node): Unit =
    statements += Mpl.Delete(Mrl.Triple(s, p, o))

  def transactionStart(): Unit =
    statements += Mpl.TxStart

  def transactionCommit(): Unit =
    statements += Mpl.TxCommit

  def transactionAbort(): Unit =
    statements += Mpl.TxAbort

  def addNamespace(name: String, iriValue: Mrl.Node): Unit =
    statements += Mpl.Add(Mpl.NsDecl(name, iriValue.asInstanceOf[Mrl.Iri]))

  def deleteNamespace(name: String, iriValue: Mrl.Node): Unit =
    statements += Mpl.Delete(Mpl.NsDecl(name, iriValue.asInstanceOf[Mrl.Iri]))

  def header(key: String, value: Mrl.Node): Unit =
    statements += Mpl.Header(key, value)

  def punctuation(): Unit =
    statements += Mpl.Punctuation
