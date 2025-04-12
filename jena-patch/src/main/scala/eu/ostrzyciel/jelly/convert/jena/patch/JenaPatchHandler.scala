package eu.ostrzyciel.jelly.convert.jena.patch

import eu.ostrzyciel.jelly.core.patch.handler.AnyPatchHandler
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.rdfpatch.RDFChanges

import scala.annotation.experimental

@experimental
private object JenaPatchHandler:
  private[patch] class JellyToJena(jenaStream: RDFChanges) extends AnyPatchHandler[Node]:
    def addQuad(s: Node, p: Node, o: Node, g: Node): Unit =
      jenaStream.add(g, s, p, o)

    def deleteQuad(s: Node, p: Node, o: Node, g: Node): Unit =
      jenaStream.delete(g, s, p, o)

    def addTriple(s: Node, p: Node, o: Node): Unit =
      jenaStream.add(null, s, p, o)

    def deleteTriple(s: Node, p: Node, o: Node): Unit =
      jenaStream.delete(null, s, p, o)

    def transactionStart(): Unit = jenaStream.txnBegin()

    def transactionCommit(): Unit = jenaStream.txnCommit()

    def transactionAbort(): Unit = jenaStream.txnAbort()

    // TODO: we encode the IRI here as a Node, but we should probably use a String
    def addNamespace(name: String, iriValue: Node, graph: Node): Unit =
      jenaStream.addPrefix(graph, name, iriValue.getURI)

    def deleteNamespace(name: String, iriValue: Node, graph: Node): Unit =
      jenaStream.deletePrefix(graph, name)

    def header(key: String, value: Node): Unit = jenaStream.header(key, value)

    def punctuation(): Unit = jenaStream.segment()

  private[patch] class JenaToJelly(jellyStream: AnyPatchHandler[Node]) extends RDFChanges:
    def add(g: Node, s: Node, p: Node, o: Node): Unit =
      if g == null then jellyStream.addTriple(s, p, o)
      else jellyStream.addQuad(s, p, o, g)

    def delete(g: Node, s: Node, p: Node, o: Node): Unit =
      if g == null then jellyStream.deleteTriple(s, p, o)
      else jellyStream.deleteQuad(s, p, o, g)

    def txnBegin(): Unit = jellyStream.transactionStart()

    def txnCommit(): Unit = jellyStream.transactionCommit()

    def txnAbort(): Unit = jellyStream.transactionAbort()

    def addPrefix(g: Node, prefix: String, uriValue: String): Unit =
      jellyStream.addNamespace(prefix, NodeFactory.createURI(uriValue), g)

    def deletePrefix(g: Node, prefix: String): Unit =
      jellyStream.deleteNamespace(prefix, null, g)

    def header(field: String, value: Node): Unit = jellyStream.header(field, value)

    def segment(): Unit = jellyStream.punctuation()

    def start(): Unit = ()

    def finish(): Unit = ()
