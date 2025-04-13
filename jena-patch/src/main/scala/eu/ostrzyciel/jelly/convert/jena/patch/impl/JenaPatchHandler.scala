package eu.ostrzyciel.jelly.convert.jena.patch.impl

import eu.ostrzyciel.jelly.core.patch.handler.AnyPatchHandler
import eu.ostrzyciel.jelly.core.proto.v1.patch.PatchStatementType
import org.apache.jena.graph.{Node, NodeFactory}
import org.apache.jena.rdfpatch.RDFChanges

import scala.annotation.{experimental, switch}

/**
 * Internal converters between Jelly-Patch streams and Jena RDFChanges streams.
 *
 * Use `JellyPatchOps` for public API to create these converters.
 */
@experimental
private[patch] object JenaPatchHandler:
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

    def addNamespace(name: String, iriValue: Node, graph: Node): Unit =
      jenaStream.addPrefix(graph, name, iriValue.getURI)

    def deleteNamespace(name: String, iriValue: Node, graph: Node): Unit =
      jenaStream.deletePrefix(graph, name)

    def header(key: String, value: Node): Unit = jenaStream.header(key, value)

    def punctuation(): Unit = jenaStream.segment()

  private[patch] class JenaToJelly(
    jellyStream: AnyPatchHandler[Node], outType: PatchStatementType
  ) extends RDFChanges:
    def add(g: Node, s: Node, p: Node, o: Node): Unit =
      (outType.value : @switch) match
        case PatchStatementType.TRIPLES.value => jellyStream.addTriple(s, p, o)
        case PatchStatementType.QUADS.value => jellyStream.addQuad(s, p, o, g)
        case _ =>
          if g == null then jellyStream.addTriple(s, p, o)
          else jellyStream.addQuad(s, p, o, g)

    def delete(g: Node, s: Node, p: Node, o: Node): Unit =
      (outType.value : @switch) match
        case PatchStatementType.TRIPLES.value => jellyStream.deleteTriple(s, p, o)
        case PatchStatementType.QUADS.value => jellyStream.deleteQuad(s, p, o, g)
        case _ =>
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
